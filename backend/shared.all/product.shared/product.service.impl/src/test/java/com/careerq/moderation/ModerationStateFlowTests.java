package com.careerq.moderation;

import com.careerq.dao.UserDao;
import com.careerq.dataobject.UserDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审核态与可见态解耦后的状态矩阵：L2 通过即可见、人工通过/拒绝/下架/恢复。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModerationStateFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserDao userDao;

    private MockHttpSession register(String phone, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", nickname))))
                .andReturn();
        if (result.getResponse().getStatus() == 200) {
            return (MockHttpSession) result.getRequest().getSession(false);
        }
        if (result.getResponse().getStatus() != 409) {
            throw new IllegalStateException("注册失败，status=" + result.getResponse().getStatus());
        }
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private MockHttpSession loginAs(String phone, String role) throws Exception {
        MockHttpSession session = register(phone, "状态测试" + role);
        MvcResult me = mockMvc.perform(get("/api/v1/me").session(session)).andReturn();
        long id = objectMapper.readTree(me.getResponse().getContentAsString()).path("data").path("id").asLong();
        UserDO user = userDao.findById(id).orElseThrow();
        user.setRole(role);
        userDao.updateRole(user);
        MvcResult relogin = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) relogin.getRequest().getSession(false);
    }

    private long createQuestion(MockHttpSession student, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions").with(csrf())
                        .session(student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "body", "这是一段正常的公开问题描述，用于验证审核状态流转。",
                                "tags", List.of(),
                                "scope", "public"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayStatus").value("PUBLISHED"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void decide(MockHttpSession operator, String type, long id, String decision) throws Exception {
        mockMvc.perform(post("/api/v1/admin/moderation/" + type + "/" + id + "/decide")
                        .session(operator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", decision, "note", "状态测试"))))
                .andExpect(status().isOk());
    }

    private JsonNode reviewed(MockHttpSession operator, String status) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/moderation/reviewed")
                        .param("type", "question").param("status", status).param("size", "50")
                        .session(operator))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("items");
    }

    private JsonNode find(JsonNode items, long questionId) {
        for (JsonNode item : items) {
            if ("question".equals(item.path("targetType").asText()) && item.path("targetId").asLong() == questionId) {
                return item;
            }
        }
        return null;
    }

    @Test
    void l2PassPublishesThenApproveRemoveAndRestore() throws Exception {
        MockHttpSession student = register("13800000941", "状态学生");
        long questionId = createQuestion(student, "L2 通过即可见");

        // L2 通过：进入人工复核队列，同时对外公开
        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.displayStatus").value("PUBLISHED"));

        MockHttpSession operator = loginAs("13800000942", "operator");
        decide(operator, "question", questionId, "approve");
        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.displayStatus").value("PUBLISHED"));

        decide(operator, "question", questionId, "remove");
        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/questions"))
                .andExpect(jsonPath("$.data.items[?(@.id == " + questionId + ")]").isEmpty());

        decide(operator, "question", questionId, "restore");
        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.displayStatus").value("PUBLISHED"));
    }

    @Test
    void rejectHidesContentAndReviewedTabKeepsDecision() throws Exception {
        MockHttpSession student = register("13800000943", "拒绝学生");
        long questionId = createQuestion(student, "人工拒绝的问题");

        MockHttpSession operator = loginAs("13800000944", "operator");
        decide(operator, "question", questionId, "reject");

        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isNotFound());

        JsonNode rejected = find(reviewed(operator, "REJECTED"), questionId);
        assertNotNull(rejected, "我审核的应包含被拒绝问题");
        assertEquals("REJECTED", rejected.path("displayStatus").asText());
        assertEquals("reject", rejected.path("reviewDecision").asText());
        assertTrue(rejected.path("reviewedAt").asText().length() > 0);
    }
}