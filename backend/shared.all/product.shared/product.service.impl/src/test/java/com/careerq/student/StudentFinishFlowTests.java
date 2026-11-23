package com.careerq.student;

import com.careerq.service.StudentNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentFinishFlowTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StudentNotificationService notifications;
    @Autowired PlatformTransactionManager transactionManager;
    static final AtomicInteger PHONES = new AtomicInteger(1000);

    private MockHttpSession account(String role) throws Exception {
        String phone = "1397900" + PHONES.incrementAndGet();
        var result = mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", phone, "code", "123456", "nickname", "消息回归",
                                "role", role.equals("operator") ? "student" : role, "company", "某行业企业"))))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (!role.equals("operator")) return session;
        long id = read(session, "/me").path("id").asLong();
        jdbc.update("update users set role = 'operator' where id = ?", id);
        var login = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }
    private JsonNode read(MockHttpSession session, String path) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1" + path).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
    }
    private JsonNode write(MockHttpSession session, String path, Map<String, Object> body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1" + path).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
    }

    private long question(MockHttpSession student, String body) throws Exception {
        return write(student, "/questions", Map.of("title", "学生收尾问题", "body", body)).path("id").asLong();
    }
    private long answer(MockHttpSession hr, long question, String body) throws Exception {
        return write(hr, "/questions/" + question + "/answers", Map.of("body", body)).path("id").asLong();
    }
    private long count(MockHttpSession student) throws Exception { return read(student, "/student/notifications/unread-count").path("total").asLong(); }
    private void decide(MockHttpSession op, String type, long id, String decision) throws Exception {
        write(op, "/admin/moderation/" + type + "/" + id + "/decide", Map.of("decision", decision, "note", "INTERNAL_ONLY"));
    }
    @Test
    void repliesLikesIsolationAndDeduplication() throws Exception {
        var student = account("student"); var other = account("student"); var hr = account("hr");
        assertEquals(0, count(student));
        long q = question(student, "正常的职业发展问题");
        long a = answer(hr, q, "真实的 HR 回答内容");
        notifications.answerPublished(a);
        assertEquals(1, count(student));
        assertEquals(0, count(other));
        var item = read(student, "/student/notifications").path("items").get(0);
        assertTrue(item.path("message").asText().contains("真实的 HR 回答内容"));
        mvc.perform(post("/api/v1/student/notifications/{id}/read", item.path("id").asLong()).session(other).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/notifications").session(hr)).andExpect(status().isForbidden());
        long f = write(student, "/questions/" + q + "/followups", Map.of("rootAnswerId", a,
                "hrId", read(hr, "/me").path("id").asLong(), "body", "如何继续准备？")).path("id").asLong();
        write(hr, "/questions/" + q + "/followups/" + f + "/answers", Map.of("body", "真实追问回复"));
        assertEquals(2, count(student));
        write(student, "/interactions", Map.of("targetType", "question", "targetId", q, "type", "like"));
        assertEquals(2, count(student));
        write(other, "/interactions", Map.of("targetType", "question", "targetId", q, "type", "like"));
        write(other, "/interactions", Map.of("targetType", "question", "targetId", q, "type", "like"));
        mvc.perform(delete("/api/v1/interactions").session(other).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("targetType", "question", "targetId", q, "type", "like")))).andExpect(status().isOk());
        write(other, "/interactions", Map.of("targetType", "question", "targetId", q, "type", "like"));
        assertEquals(3, count(student));
        assertEquals(1, read(student, "/student/notifications/unread-count").path("like").asInt());
        write(hr, "/answers/" + a + "/withdraw", Map.of());
        var rows = read(student, "/student/notifications?category=reply").path("items");
        for (var row : rows) if (row.path("answerId").asLong() == a) {
            assertEquals("unavailable", row.path("destination").asText());
            assertFalse(row.path("message").asText().contains("真实的 HR 回答内容"));
        }
    }
    @Test
    void moderationMessagesAndSnapshotReads() throws Exception {
        var student = account("student"); var hr = account("hr"); var op = account("operator");
        long pending = question(student, "可以加微信获取联系方式吗？");
        assertEquals(0, count(student));
        decide(op, "question", pending, "reject");
        var rejected = read(student, "/student/notifications").path("items").get(0);
        assertEquals("question_rejected", rejected.path("type").asText());
        assertFalse(rejected.toString().contains("INTERNAL_ONLY"));
        long q = question(student, "正常面试准备问题");
        long a = answer(hr, q, "加微信讨论联系方式");
        assertEquals(2, count(student));
        decide(op, "answer", a, "approve");
        assertEquals(2, count(student));
        long f = write(student, "/questions/" + q + "/followups", Map.of("rootAnswerId", a,
                "hrId", read(hr, "/me").path("id").asLong(), "body", "加微信讨论联系方式")).path("id").asLong();
        decide(op, "followup", f, "approve");
        var first = read(student, "/student/notifications?limit=1");
        long snapshot = first.path("snapshotId").asLong();
        assertFalse(first.path("nextCursor").isNull());
        long later = answer(hr, q, "这是快照之后的新回答");
        write(student, "/student/notifications/read-all", Map.of("throughId", snapshot));
        assertEquals(1, count(student));
        var unread = read(student, "/student/notifications?unreadOnly=true").path("items");
        assertEquals(later, unread.get(0).path("answerId").asLong());
        long notice = unread.get(0).path("id").asLong();
        var once = write(student, "/student/notifications/" + notice + "/read", Map.of());
        var twice = write(student, "/student/notifications/" + notice + "/read", Map.of());
        assertEquals(once.path("readAt"), twice.path("readAt")); assertEquals(0, count(student));
        decide(op, "question", q, "remove");
        assertEquals("question_removed", read(student, "/student/notifications").path("items").get(0).path("type").asText());
    }
    @Test
    void favoritesFilterBeforePagingAndLinkToAnswerContext() throws Exception {
        var student = account("student"); var other = account("student"); var hr = account("hr");
        long visible = question(student, "公开收藏问题"); long hidden = question(student, "即将隐藏的问题");
        long a = answer(hr, visible, "被收藏的回答");
        for (long id : new long[]{visible, hidden}) write(student, "/interactions", Map.of("targetType", "question", "targetId", id, "type", "favorite"));
        write(student, "/interactions", Map.of("targetType", "answer", "targetId", a, "type", "favorite"));
        jdbc.update("update questions set visibility = 'REMOVED', moderation_status = 'APPROVED' where id = ?", hidden);
        var first = read(student, "/me/favorites?limit=1");
        var answer = first.path("items").get(0);
        assertEquals(a, answer.path("targetId").asLong()); assertEquals(visible, answer.path("questionId").asLong());
        var second = read(student, "/me/favorites?limit=1&cursor=" + first.path("nextCursor").asLong());
        assertEquals(visible, second.path("items").get(0).path("targetId").asLong());
        assertTrue(second.path("nextCursor").isNull() || second.path("nextCursor").isMissingNode());
        assertEquals(0, read(other, "/me/favorites").path("items").size());
    }
    @Test
    void businessRollbackAlsoRollsBackNotification() throws Exception {
        var student = account("student"); var hr = account("hr"); long q = question(student, "回滚验证问题");
        long a = answer(hr, q, "回滚验证回答");
        long studentId = read(student, "/me").path("id").asLong();
        jdbc.update("delete from student_notifications where recipient_id = ?", studentId);
        var transaction = new TransactionTemplate(transactionManager);
        // The outer test transaction owns these rows, so exercise rollback with a savepoint instead.
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
        transaction.execute(status -> { notifications.answerPublished(a); status.setRollbackOnly(); return null; });
        assertEquals(0, count(student));
    }
    @Test
    void migrationBackfillsOnlyGenuineVisibleRepliesAndLikes() {
        String url = "jdbc:h2:mem:student_upgrade_" + java.util.UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("16").load().migrate();
        var old = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        for (int id = 1; id <= 3; id++) old.update("insert into users (id, role, nickname, created_at, updated_at) values (?, ?, '用户', current_timestamp, current_timestamp)", id, id == 2 ? "hr" : "student");
        old.update("insert into questions (id, author_id, title, body, scope, status, created_at, updated_at) values (1, 1, '公开问题', 'body', 'public', 'published', current_timestamp, current_timestamp)");
        old.update("insert into questions (id, author_id, title, body, scope, status, created_at, updated_at) values (2, 1, '隐藏问题', 'body', 'public', 'removed', current_timestamp, current_timestamp)");
        old.update("insert into answers (id, question_id, author_id, body, status, created_at, updated_at) values (1, 1, 2, '真实回答', 'published', current_timestamp, current_timestamp)");
        old.update("insert into answers (id, question_id, author_id, body, status, created_at, updated_at) values (2, 1, 2, '待审核回答', 'pending_human', current_timestamp, current_timestamp)");
        old.update("insert into answers (id, question_id, author_id, body, status, created_at, updated_at) values (3, 2, 2, '隐藏回答', 'published', current_timestamp, current_timestamp)");
        for (int actor : new int[]{1, 3}) old.update("insert into interactions (user_id, target_type, target_id, type, created_at) values (?, 'question', 1, 'like', current_timestamp)", actor);
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        assertEquals(2, old.queryForObject("select count(*) from student_notifications", Integer.class));
        assertEquals(1, old.queryForObject("select count(*) from student_notifications where type = 'answer_published' and answer_id = 1", Integer.class));
        assertEquals(1, old.queryForObject("select count(*) from student_notifications where event_key = 'question_liked:1:3'", Integer.class));
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        assertEquals(2, old.queryForObject("select count(*) from student_notifications", Integer.class));
        old.execute("shutdown");
    }
}
