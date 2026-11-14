package com.careerq.moderation;

import com.careerq.user.User;
import com.careerq.user.UserRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ModerationFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private MockHttpSession login(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long userId(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/me").session(session)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private MockHttpSession loginAs(String phone, String role) throws Exception {
        MockHttpSession session = login(phone);
        long id = userId(session);
        User user = userRepository.findById(id).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
        return login(phone);
    }

    private long createQuestion(MockHttpSession session, String title, String body, String scope) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title, "body", body, "scope", scope))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private JsonNode queue(MockHttpSession operator) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/moderation/queue").session(operator))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private boolean queueContains(JsonNode items, String targetType, long targetId) {
        for (JsonNode item : items) {
            if (targetType.equals(item.path("targetType").asText()) && item.path("targetId").asLong() == targetId) {
                return true;
            }
        }
        return false;
    }

    @Test
    void riskyQuestionGoesToQueueThenApproved() throws Exception {
        MockHttpSession student = login("13800000301");
        long questionId = createQuestion(student, "想找内推", "可以加微信详聊吗？", "public");

        // 命中高风险词 → 待人工审核，公开不可见
        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isNotFound());

        MockHttpSession operator = loginAs("13800000302", "operator");
        assertTrue(queueContains(queue(operator), "question", questionId), "队列应包含待审问题");

        mockMvc.perform(post("/api/v1/admin/moderation/question/{id}/decide", questionId)
                        .session(operator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "approve", "note", "人工确认"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.status").value("published"));
    }

    @Test
    void reportAndRemoveAnswer() throws Exception {
        MockHttpSession student = login("13800000303");
        long questionId = createQuestion(student, "如何准备群面？", "想了解群面技巧。", "public");

        MockHttpSession hr = loginAs("13800000304", "hr");
        MvcResult answerResult = mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "群面要注意分工与表达。"))))
                .andExpect(status().isOk()).andReturn();
        long answerId = objectMapper.readTree(answerResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/reports")
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "answer", "targetId", answerId,
                                "reason", "misleading", "detail", "内容有误导"))))
                .andExpect(status().isOk());

        MockHttpSession operator = loginAs("13800000305", "operator");
        MvcResult reportsResult = mockMvc.perform(get("/api/v1/admin/reports?status=pending").session(operator))
                .andExpect(status().isOk()).andReturn();
        JsonNode reports = objectMapper.readTree(reportsResult.getResponse().getContentAsString()).path("data");
        long reportId = reports.get(0).path("id").asLong();

        mockMvc.perform(post("/api/v1/admin/reports/{id}/handle", reportId)
                        .session(operator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "remove", "note", "确认误导"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers.length()").value(0));
    }

    @Test
    void publicPostRequiresModerationBeforeListing() throws Exception {
        MockHttpSession student = login("13800000306");
        long questionId = createQuestion(student, "试用期被辞退怎么办？", "想了解试用期辞退的合法流程。", "private");

        MockHttpSession hr = loginAs("13800000307", "hr");
        mockMvc.perform(post("/api/v1/hr/questions/{id}/claim", questionId).session(hr).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "先确认辞退理由与证据，再谈补偿。"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/questions/{id}/close", questionId).session(student).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/questions/{id}/publish", questionId).session(student).with(csrf()))
                .andExpect(status().isOk());

        // 审核前公开列表不含
        MvcResult before = mockMvc.perform(get("/api/v1/posts")).andExpect(status().isOk()).andReturn();
        assertFalse(objectMapper.readTree(before.getResponse().getContentAsString())
                .path("data").toString().contains("试用期被辞退怎么办"));

        MockHttpSession operator = loginAs("13800000308", "operator");
        MvcResult queueResult = mockMvc.perform(get("/api/v1/admin/moderation/queue").session(operator))
                .andExpect(status().isOk()).andReturn();
        JsonNode items = objectMapper.readTree(queueResult.getResponse().getContentAsString()).path("data");
        long postId = -1;
        for (JsonNode item : items) {
            if ("public_post".equals(item.path("targetType").asText())
                    && item.path("title").asText().contains("试用期被辞退怎么办")) {
                postId = item.path("targetId").asLong();
            }
        }
        assertTrue(postId > 0, "队列应包含待审公开帖");

        mockMvc.perform(post("/api/v1/admin/moderation/public_post/{id}/decide", postId)
                        .session(operator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "approve"))))
                .andExpect(status().isOk());

        MvcResult after = mockMvc.perform(get("/api/v1/posts")).andExpect(status().isOk()).andReturn();
        assertTrue(objectMapper.readTree(after.getResponse().getContentAsString())
                .path("data").toString().contains("试用期被辞退怎么办"));
    }

    @Test
    void blockWordRejectsContent() throws Exception {
        MockHttpSession operator = loginAs("13800000309", "operator");
        mockMvc.perform(post("/api/v1/admin/moderation/words")
                        .session(operator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "word", "违禁词TEST", "category", "test", "action", "block"))))
                .andExpect(status().isOk());

        MockHttpSession student = login("13800000310");
        MvcResult result = mockMvc.perform(post("/api/v1/questions")
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "测试违禁词", "body", "这里包含违禁词TEST", "scope", "public"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("rejected"))
                .andReturn();
        long questionId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonOperatorCannotAccessAdmin() throws Exception {
        MockHttpSession student = login("13800000311");
        mockMvc.perform(get("/api/v1/admin/moderation/queue").session(student))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/metrics").session(student))
                .andExpect(status().isForbidden());
    }

    @Test
    void metricsCountsPendingItems() throws Exception {
        MockHttpSession operator = loginAs("13800000312", "operator");
        MvcResult result = mockMvc.perform(get("/api/v1/admin/metrics").session(operator))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertTrue(data.has("questionsPendingHuman"));
        assertTrue(data.has("reportsPending"));
        assertTrue(data.path("questionsPendingHuman").asInt() >= 0);
    }
}
