package com.careerq.question;

import com.careerq.dao.ModerationWordDao;
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
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentReviewFixFlowTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ModerationWordDao words;
    static final AtomicInteger PHONES = new AtomicInteger(1000);
    private record Account(String phone, MockHttpSession session) {}
    private Account account(String role, Map<String, Object> extras) throws Exception {
        String phone = "1397700" + PHONES.incrementAndGet();
        var body = new java.util.HashMap<String, Object>(extras);
        body.putAll(Map.of("phone", phone, "code", "123456", "nickname", "学生修复回归", "role", role.equals("operator") ? "student" : role, "company", "某行业企业"));
        var result = mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        if (role.equals("operator")) {
            long id = read(session, "/me").path("id").asLong();
            jdbc.update("update users set role = 'operator' where id = ?", id);
            session = login(phone);
        }
        return new Account(phone, session);
    }
    private MockHttpSession login(String phone) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private JsonNode read(MockHttpSession session, String path) throws Exception {
        var req = get("/api/v1" + path);
        if (session != null) req.session(session);
        return json.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private JsonNode write(MockHttpSession session, String path, Map<String, Object> body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1" + path).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private record Scenario(MockHttpSession student, MockHttpSession hr, MockHttpSession operator, long question, long root, long hrId) {
        String base() { return "/questions/" + question; }
    }
    private Scenario scenario() throws Exception {
        var student = account("student", Map.of()).session();
        var hr = account("hr", Map.of()).session();
        var op = account("operator", Map.of()).session();
        long question = write(student, "/questions", Map.of("title", "学生审核验证", "body", "如何准备实习面试？")).path("id").asLong();
        long root = write(hr, "/questions/" + question + "/answers", Map.of("body", "先梳理岗位能力和实践经历")).path("id").asLong();
        return new Scenario(student, hr, op, question, root, read(hr, "/me").path("id").asLong());
    }
    private JsonNode followup(Scenario s, String body) throws Exception {
        return write(s.student(), s.base() + "/followups", Map.of("rootAnswerId", s.root(), "hrId", s.hrId(), "body", body));
    }

    @Test
    void registrationPreservesOptionalEducationAcrossLoginAndValidatesLengths() throws Exception {
        var account = account("student", Map.of("school", "  某大学  ", "major", "计算机科学", "grade", "大三"));
        for (var session : new MockHttpSession[]{account.session(), login(account.phone())}) {
            var profile = read(session, "/me/profile");
            assertEquals("某大学", profile.path("school").asText());
            assertEquals("计算机科学", profile.path("major").asText());
            assertEquals("大三", profile.path("grade").asText());
            assertEquals("某大学", read(session, "/me").path("school").asText());
        }
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", "13977009999", "code", "123456", "school", "校".repeat(129)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staleStudentIdentityCannotCreateOrModifyContentForAnotherSession() throws Exception {
        var first = account("student", Map.of()).session();
        var second = account("student", Map.of()).session();
        long firstId = read(first, "/me").path("id").asLong();
        long secondId = read(second, "/me").path("id").asLong();
        long before = read(second, "/me/stats").path("questionCount").asLong();
        var response = mvc.perform(post("/api/v1/questions").session(second).with(csrf())
                        .header("X-CareerQ-Student-Id", firstId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"不应创建\",\"body\":\"旧页写请求\"}"))
                .andExpect(status().isConflict()).andReturn();
        assertEquals(40902, json.readTree(response.getResponse().getContentAsString()).path("code").asInt());
        assertEquals(before, read(second, "/me/stats").path("questionCount").asLong());
        mvc.perform(patch("/api/v1/me/profile").session(second).with(csrf()).header("X-CareerQ-Student-Id", firstId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"school\":\"不应保存\"}"))
                .andExpect(status().isConflict());
        assertFalse(read(second, "/me/profile").has("school"));
        mvc.perform(post("/api/v1/questions").session(second).with(csrf()).header("X-CareerQ-Student-Id", secondId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"正确身份\",\"body\":\"正常提交\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void followupReviewControlsVisibilityCountersAndPublicationOnlyAfterApproval() throws Exception {
        var s = scenario();
        var pending = followup(s, "我可以加微信获取联系方式吗？");
        assertEquals("pending_human", pending.path("status").asText());
        long id = pending.path("id").asLong();
        assertEquals(0, read(null, s.base()).path("followups").size());
        assertEquals(1, read(s.student(), s.base()).path("followups").size());
        assertEquals(0, read(s.hr(), s.base()).path("followups").size());
        assertEquals(0, read(s.hr(), "/hr/notifications/unread-count").path("total").asInt());
        assertEquals(0, read(null, s.base()).path("question").path("followupCount").asInt());
        boolean queued = false;
        for (JsonNode item : read(s.operator(), "/admin/moderation/queue").path("items")) {
            if (item.path("targetType").asText().equals("followup") && item.path("targetId").asLong() == id) queued = true;
        }
        assertTrue(queued);
        write(s.operator(), "/admin/moderation/followup/" + id + "/decide", Map.of("decision", "approve"));
        assertEquals(1, read(null, s.base()).path("followups").size());
        assertEquals(1, read(null, s.base()).path("question").path("followupCount").asInt());
        assertEquals(1, read(s.hr(), "/hr/notifications/unread-count").path("total").asInt());
        mvc.perform(post("/api/v1/admin/moderation/followup/{id}/decide", id).session(s.operator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"approve\"}"))
                .andExpect(status().isConflict());
        write(s.operator(), "/admin/moderation/followup/" + id + "/decide", Map.of("decision", "remove"));
        assertEquals(0, read(null, s.base()).path("question").path("followupCount").asInt());
    }

    @Test
    void blockedOrWithdrawnPendingFollowupsNeverBecomePublicOrCounted() throws Exception {
        var s = scenario();
        words.upsert("学生审核拦截词", "test", "block");
        var blocked = followup(s, "学生审核拦截词");
        assertEquals("rejected", blocked.path("status").asText());
        assertEquals(0, read(null, s.base()).path("followups").size());
        var pending = followup(s, "可以加微信咨询吗？");
        write(s.student(), s.base() + "/followups/" + pending.path("id").asLong() + "/withdraw", Map.of());
        assertEquals(0, read(null, s.base()).path("question").path("followupCount").asInt());
        assertEquals(0, read(s.hr(), "/hr/notifications/unread-count").path("total").asInt());
        assertEquals(1, read(s.student(), s.base()).path("followups").size());
        assertEquals("rejected", read(s.student(), s.base()).path("followups").get(0).path("status").asText());
    }
}
