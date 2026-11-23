package com.careerq.hr;

import com.careerq.service.HrNotificationService;
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
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HrNotificationFlowTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired HrNotificationService notifications;
    @Autowired PlatformTransactionManager transactionManager;
    static final AtomicInteger PHONES = new AtomicInteger(1000);

    private MockHttpSession account(String role) throws Exception {
        String phone = "1397600" + PHONES.incrementAndGet();
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
    private record Scenario(MockHttpSession student, MockHttpSession hr, MockHttpSession other, MockHttpSession operator,
                            long question, long root, long hrId) {
        String base() { return "/questions/" + question; }
    }
    private Scenario scenario() throws Exception {
        var student = account("student"); var hr = account("hr"); var other = account("hr"); var operator = account("operator");
        long q = write(student, "/questions", Map.of("title", "消息验证问题", "body", "如何准备面试？")).path("id").asLong();
        long root = write(hr, "/questions/" + q + "/answers", Map.of("body", "从岗位能力和项目经验开始准备")).path("id").asLong();
        return new Scenario(student, hr, other, operator, q, root, read(hr, "/me").path("id").asLong());
    }
    private long followup(Scenario s) throws Exception {
        return write(s.student(), s.base() + "/followups", Map.of("hrId", s.hrId(), "rootAnswerId", s.root(), "body", "请问具体如何准备？"))
                .path("id").asLong();
    }
    private JsonNode list(Scenario s) throws Exception { return read(s.hr(), "/hr/notifications"); }
    private long total(Scenario s) throws Exception { return read(s.hr(), "/hr/notifications/unread-count").path("total").asLong(); }
    private void decide(Scenario s, long id, String decision) throws Exception {
        write(s.operator(), "/admin/moderation/answer/" + id + "/decide", Map.of("decision", decision, "note", "INTERNAL_ONLY_审核备注不公开"));
    }

    @Test
    void followupTargetsOnlyItsHrAndReadingNeverClearsPendingWork() throws Exception {
        var s = scenario();
        assertEquals(0, total(s));
        long f = followup(s);
        notifications.followupPublished(f);
        assertEquals(1, total(s));
        assertEquals(0, read(s.other(), "/hr/notifications/unread-count").path("total").asInt());
        JsonNode item = list(s).path("items").get(0);
        assertEquals("followup_published", item.path("type").asText());
        assertEquals("question", item.path("destination").asText());
        assertEquals(s.root(), item.path("answerId").asLong());
        assertEquals(f, item.path("followupId").asLong());
        assertFalse(item.has("body"));
        long id = item.path("id").asLong();
        mvc.perform(post("/api/v1/hr/notifications/{id}/read", id).session(s.other()).with(csrf())).andExpect(status().isNotFound());
        var firstRead = write(s.hr(), "/hr/notifications/" + id + "/read", Map.of());
        assertEquals(0, total(s));
        assertEquals(firstRead.path("readAt"), write(s.hr(), "/hr/notifications/" + id + "/read", Map.of()).path("readAt"));
        var pending = read(s.hr(), "/hr/answered-questions?pendingFollowupsOnly=true");
        assertEquals(1, pending.path("pendingFollowupCount").asInt());
        for (String endpoint : new String[]{"/hr/notifications", "/hr/notifications/unread-count"}) {
            mvc.perform(get("/api/v1" + endpoint).session(s.student())).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1" + endpoint)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/hr/notifications/read-all").session(s.student()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"throughId\":999}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void paginationAndReadAllUseSnapshotWithoutSwallowingLaterMessages() throws Exception {
        var s = scenario();
        for (int i = 0; i < 6; i++) followup(s);
        JsonNode first = read(s.hr(), "/hr/notifications?limit=5&category=followup&unreadOnly=true");
        assertEquals(5, first.path("items").size());
        long snapshot = first.path("snapshotId").asLong();
        JsonNode second = read(s.hr(), "/hr/notifications?limit=5&category=followup&unreadOnly=true&cursor=" + first.path("nextCursor").asLong());
        assertEquals(1, second.path("items").size());
        assertTrue(second.path("nextCursor").isMissingNode() || second.path("nextCursor").isNull());
        assertTrue(second.path("items").get(0).path("id").asLong() < first.path("nextCursor").asLong());
        followup(s);
        write(s.hr(), "/hr/notifications/read-all", Map.of("throughId", snapshot));
        assertEquals(1, total(s));
        assertEquals(1, read(s.hr(), "/hr/notifications?unreadOnly=true").path("items").size());
        for (String query : new String[]{"category=bad", "limit=0", "limit=51", "cursor=-1"}) {
            mvc.perform(get("/api/v1/hr/notifications?" + query).session(s.hr())).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/hr/notifications/read-all").session(s.hr()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"throughId\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moderationAndReportRemovalNotifyOnceAndNeverExposeInternalNotesOrHiddenTitles() throws Exception {
        var s = scenario();
        long pending = write(s.hr(), s.base() + "/answers", Map.of("body", "联系方式稍后提供")).path("id").asLong();
        assertEquals(0, total(s));
        decide(s, pending, "approve");
        notifications.answerReviewed(pending, "approve");
        assertEquals(1, total(s));
        decide(s, pending, "remove"); decide(s, pending, "remove");
        assertEquals(2, total(s));
        long rejected = write(s.hr(), s.base() + "/answers", Map.of("body", "可以加微信咨询")).path("id").asLong();
        decide(s, rejected, "reject");
        assertEquals(3, total(s));
        long replyTo = followup(s);
        long reply = write(s.hr(), s.base() + "/followups/" + replyTo + "/answers", Map.of("body", "这是具体回复")).path("id").asLong();
        var reportBody = Map.<String, Object>of("targetType", "answer", "targetId", reply, "reason", "misleading");
        mvc.perform(post("/api/v1/reports").session(s.hr()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(reportBody)))
                .andExpect(status().isForbidden());
        write(s.student(), "/reports", reportBody);
        long reportId = 0;
        for (JsonNode report : read(s.operator(), "/admin/reports?status=pending")) if (report.path("targetId").asLong() == reply) reportId = report.path("id").asLong();
        assertTrue(reportId > 0);
        write(s.operator(), "/admin/reports/" + reportId + "/handle", Map.of("decision", "remove", "note", "INTERNAL_ONLY_举报备注"));
        decide(s, reply, "remove");
        assertEquals(5, total(s));
        JsonNode messages = read(s.hr(), "/hr/notifications?limit=50");
        assertEquals(4, read(s.hr(), "/hr/notifications/unread-count").path("system").asInt());
        assertFalse(messages.toString().contains("INTERNAL_ONLY"));
        assertEquals("answer_record", messages.path("items").get(0).path("destination").asText());
        assertEquals("operator", read(s.hr(), "/hr/answers/" + reply).path("removalSource").asText());
        mvc.perform(get("/api/v1/hr/answers/{id}", reply).session(s.other())).andExpect(status().isNotFound());
        write(s.operator(), "/admin/moderation/question/" + s.question() + "/decide", Map.of("decision", "remove"));
        messages = read(s.hr(), "/hr/notifications?limit=50");
        assertFalse(messages.toString().contains("消息验证问题"));
        for (JsonNode item : messages.path("items")) if (item.path("type").asText().equals("followup_published")) {
            assertEquals("unavailable", item.path("destination").asText());
            assertTrue(item.path("message").asText().contains("不可查看"));
        }
    }

    @Test
    void withdrawalDoesNotGenerateDownlistingNoticeAndWithdrawnFollowupBecomesUnavailable() throws Exception {
        var s = scenario();
        long f = followup(s);
        write(s.student(), s.base() + "/followups/" + f + "/withdraw", Map.of());
        assertEquals("unavailable", list(s).path("items").get(0).path("destination").asText());
        write(s.hr(), "/answers/" + s.root() + "/withdraw", Map.of());
        decide(s, s.root(), "remove");
        assertEquals(1, total(s));
        assertEquals(0, read(s.hr(), "/hr/notifications?category=system").path("items").size());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rolledBackFollowupNeverLeavesANotification() throws Exception {
        var s = scenario();
        var transaction = new TransactionTemplate(transactionManager);
        transaction.execute(status -> {
            try { followup(s); } catch (Exception e) { throw new RuntimeException(e); }
            status.setRollbackOnly();
            return null;
        });
        assertEquals(0, total(s));
    }
}
