package com.careerq.moderation;

import com.careerq.dao.UserDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnswerRemovalFlowTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserDao users;
    @Autowired JdbcTemplate jdbc;
    private static final AtomicInteger PHONES = new AtomicInteger(1000);

    private MockHttpSession account(String role) throws Exception {
        String phone = "1397500" + PHONES.incrementAndGet();
        var result = mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", "移除回归",
                                "role", role.equals("operator") ? "student" : role, "company", "某行业企业"))))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (!role.equals("operator")) return session;
        long id = read(session, "/me").path("id").asLong();
        var user = users.findById(id).orElseThrow();
        user.setRole("operator");
        users.updateRole(user);
        var login = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private JsonNode write(MockHttpSession session, String path, Map<String, Object> body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1" + path).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    private JsonNode read(MockHttpSession session, String path) throws Exception {
        var request = get("/api/v1" + path);
        if (session != null) request.session(session);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString()).path("data");
    }

    private record Scenario(MockHttpSession student, MockHttpSession hr, MockHttpSession operator,
                            long questionId, long answerId, long hrId) {
        String base() { return "/questions/" + questionId; }
    }

    private Scenario scenario() throws Exception {
        var student = account("student");
        var hr = account("hr");
        var operator = account("operator");
        long questionId = write(student, "/questions", Map.of("title", "面试准备", "body", "怎样准备实习面试？"))
                .path("id").asLong();
        long answerId = write(hr, "/questions/" + questionId + "/answers", Map.of("body", "原回答不可泄漏"))
                .path("id").asLong();
        return new Scenario(student, hr, operator, questionId, answerId, read(hr, "/me").path("id").asLong());
    }

    private void remove(Scenario s, long answerId) throws Exception {
        write(s.operator(), "/admin/moderation/answer/" + answerId + "/decide", Map.of("decision", "remove"));
    }

    private int count(Scenario s) throws Exception {
        return read(null, s.base()).path("question").path("answerCount").asInt();
    }

    private JsonNode find(JsonNode items, long id) {
        for (JsonNode item : items) if (item.path("id").asLong() == id) return item;
        fail("Missing item " + id);
        return null;
    }

    private long report(Scenario s, long answerId) throws Exception {
        write(s.student(), "/reports", Map.of("targetType", "answer", "targetId", answerId,
                "reason", "misleading", "detail", "请核查内容"));
        for (JsonNode item : read(s.operator(), "/admin/reports?status=pending")) {
            if (item.path("targetType").asText().equals("answer") && item.path("targetId").asLong() == answerId) {
                return item.path("id").asLong();
            }
        }
        throw new AssertionError("Missing report for answer " + answerId);
    }

    @Test
    void directRemovalKeepsSourceAndPlaceholderAndReturnsLastAnswerToPool() throws Exception {
        var s = scenario();
        write(s.student(), s.base() + "/followups", Map.of("hrId", s.hrId(), "rootAnswerId", s.answerId(), "body", "如何准备？"));
        mvc.perform(post("/api/v1/admin/moderation/answer/{id}/decide", s.answerId()).session(s.hr()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"remove\"}"))
                .andExpect(status().isForbidden());
        remove(s, s.answerId());
        assertEquals(0, count(s));
        JsonNode placeholder = find(read(null, s.base()).path("answers"), s.answerId());
        assertEquals("operator", placeholder.path("removalSource").asText());
        assertEquals("", placeholder.path("body").asText());
        assertFalse(placeholder.has("removedBy"));
        assertEquals("operator", find(read(null, s.base() + "/thread").path("items"), s.answerId()).path("removalSource").asText());
        assertNotNull(find(read(s.hr(), "/hr/question-pool?limit=50").path("items"), s.questionId()));
        var summary = read(s.hr(), "/hr/answered-questions?limit=50").path("items").get(0).path("myAnswer");
        assertEquals("operator", summary.path("removalSource").asText());
        assertEquals("", summary.path("body").asText());
        String removedAt = jdbc.queryForObject("select cast(removed_at as varchar) from answers where id = ?", String.class, s.answerId());
        remove(s, s.answerId());
        assertEquals(0, count(s));
        assertEquals(removedAt, jdbc.queryForObject("select cast(removed_at as varchar) from answers where id = ?", String.class, s.answerId()));
        var response = mvc.perform(post("/api/v1/answers/{id}/withdraw", s.answerId()).session(s.hr()).with(csrf()))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertTrue(response.contains("运营下架"));
    }

    @Test
    void reportRemovalOfReplyDecrementsOnlyOnceAndRestoresPendingFollowup() throws Exception {
        var s = scenario();
        long followupId = write(s.student(), s.base() + "/followups", Map.of("hrId", s.hrId(), "rootAnswerId", s.answerId(), "body", "具体怎么做？"))
                .path("id").asLong();
        long replyId = write(s.hr(), s.base() + "/followups/" + followupId + "/answers", Map.of("body", "具体回复"))
                .path("id").asLong();
        long reportId = report(s, replyId);
        assertEquals(2, count(s));
        write(s.operator(), "/admin/reports/" + reportId + "/handle", Map.of("decision", "remove", "note", "确认处理"));
        assertEquals(1, count(s));
        remove(s, replyId);
        assertEquals(1, count(s));
        var item = read(s.hr(), "/hr/answered-questions?pendingFollowupsOnly=true&limit=50").path("items").get(0);
        assertEquals(1, item.path("followups").path("pendingCount").asInt());
        assertEquals("operator", item.path("myAnswer").path("removalSource").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from moderation_records where target_type = 'answer' and target_id = ? and note = '确认处理'", Integer.class, replyId));
    }

    @Test
    void laterReportDoesNotReclassifyAuthorWithdrawalOrDeductAnotherAnswer() throws Exception {
        var s = scenario();
        write(s.hr(), s.base() + "/answers", Map.of("body", "另一条保留的独立回答"));
        long reportId = report(s, s.answerId());
        JsonNode withdrawn = write(s.hr(), "/answers/" + s.answerId() + "/withdraw", Map.of());
        assertEquals("author", withdrawn.path("removalSource").asText());
        assertEquals("", withdrawn.path("body").asText());
        assertEquals(1, count(s));
        write(s.operator(), "/admin/reports/" + reportId + "/handle", Map.of("decision", "remove"));
        assertEquals(1, count(s));
        assertEquals("author", jdbc.queryForObject("select removal_source from answers where id = ?", String.class, s.answerId()));
        assertEquals(s.hrId(), jdbc.queryForObject("select removed_by from answers where id = ?", Long.class, s.answerId()));
    }

    @Test
    void removingVisibleAnswersDeductsOnceAndWithdrawDoesNot() throws Exception {
        var s = scenario();
        long flaggedId = write(s.hr(), s.base() + "/answers", Map.of("body", "可加微信咨询")).path("id").asLong();
        assertEquals(2, count(s));
        remove(s, flaggedId);
        assertEquals(1, count(s));

        long secondId = write(s.hr(), s.base() + "/answers", Map.of("body", "联系方式稍后提供")).path("id").asLong();
        assertEquals(2, count(s));
        write(s.hr(), "/answers/" + secondId + "/withdraw", Map.of());
        assertEquals(1, count(s));

        remove(s, s.answerId());
        assertEquals(0, count(s));
    }

    @Test
    void migrationCorrectsExistingCountsWithoutInventingRemovalSource() {
        String url = "jdbc:h2:mem:removal_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("13").load().migrate();
        JdbcTemplate old = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        old.update("insert into questions (id, author_id, title, body, scope, status, answer_count, created_at, updated_at) values (1, 1, 'q', 'q', 'public', 'published', 4, current_timestamp, current_timestamp)");
        old.update("insert into questions (id, author_id, title, body, scope, status, answer_count, created_at, updated_at) values (2, 1, 'q', 'q', 'public', 'published', 1, current_timestamp, current_timestamp)");
        for (String state : new String[]{"published", "removed", "pending_human"}) {
            old.update("insert into answers (question_id, author_id, body, status, created_at, updated_at) values (1, 1, 'body', ?, current_timestamp, current_timestamp)", state);
        }
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        assertEquals(1, old.queryForObject("select answer_count from questions where id = 1", Integer.class));
        assertEquals(0, old.queryForObject("select answer_count from questions where id = 2", Integer.class));
        assertEquals("unknown", old.queryForObject("select removal_source from answers where visibility = 'REMOVED'", String.class));
        assertEquals(0, old.queryForObject("select count(*) from answers where visibility != 'REMOVED' and removal_source is not null", Integer.class));
    }
}
