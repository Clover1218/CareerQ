package com.careerq.admin;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审核抽屉上下文：首次只带当前±1条，上下分页按 kind:id 游标推进。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReviewContextPagingTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserDao userDao;

    private MockHttpSession login(String phone, String role) throws Exception {
        Map<String, Object> reg = new HashMap<>();
        reg.put("phone", phone);
        reg.put("code", "123456");
        reg.put("nickname", "上下文分页" + role);
        reg.put("role", role);
        if ("hr".equals(role)) {
            reg.put("company", "上下文分页公司");
        }
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk());
        return signIn(phone);
    }

    private MockHttpSession signIn(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession operator(String phone) throws Exception {
        MockHttpSession session = login(phone, "student");
        long id = currentUserId(session);
        UserDO user = userDao.findById(id).orElseThrow();
        user.setRole("operator");
        userDao.updateRole(user);
        return signIn(phone);
    }

    private long currentUserId(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long createQuestion(MockHttpSession student) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions").session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "上下文分页测试问题",
                                "body", "用于验证审核抽屉上下文窗口与上下分页。",
                                "tags", List.of(),
                                "scope", "public"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long rootAnswer(MockHttpSession hr, long questionId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "根回答：先梳理项目优先级。"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long followUp(MockHttpSession student, long questionId, long hrId, long rootAnswerId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions/{id}/followups", questionId)
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "hrId", hrId, "rootAnswerId", rootAnswerId, "body", body))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long reply(MockHttpSession hr, long questionId, long followupId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions/{id}/followups/{fid}/answers", questionId, followupId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", body))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private JsonNode detail(MockHttpSession operator, String targetType, long targetId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/moderation/{type}/{id}", targetType, targetId)
                        .session(operator))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode page(MockHttpSession operator, String targetType, long targetId,
                          String direction, String cursor, int limit) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/moderation/{type}/{id}/context/{dir}",
                                targetType, targetId, direction)
                        .param("cursor", cursor)
                        .param("limit", String.valueOf(limit))
                        .session(operator))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String keyOf(JsonNode entry) {
        return entry.path("kind").asText() + ":" + entry.path("id").asLong();
    }

    @Test
    void contextStartsAtTargetAndPagesBothWays() throws Exception {
        MockHttpSession student = login("13800006001", "student");
        MockHttpSession hr = login("13800006002", "hr");
        MockHttpSession operator = operator("13800006003");

        long questionId = createQuestion(student);
        long hrId = currentUserId(hr);
        long rootAnswerId = rootAnswer(hr, questionId);
        long followup1 = followUp(student, questionId, hrId, rootAnswerId, "追问一：简历要多长？");
        long reply1 = reply(hr, questionId, followup1, "回复一：一页即可。");
        long followup2 = followUp(student, questionId, hrId, rootAnswerId, "追问二：面试怎么准备？");
        long reply2 = reply(hr, questionId, followup2, "回复二：先过项目细节。");

        JsonNode questionDetail = detail(operator, "question", questionId);
        JsonNode window = questionDetail.path("context");
        assertEquals(2, window.size(), "问题作为锚点时应返回当前+后一条");
        assertEquals("question", window.get(0).path("kind").asText());
        assertTrue(window.get(0).path("target").asBoolean());
        assertEquals(0, questionDetail.path("contextBeforeRemaining").asInt());
        assertEquals(4, questionDetail.path("contextAfterRemaining").asInt());

        Set<String> seen = new HashSet<>();
        for (JsonNode entry : window) {
            assertTrue(seen.add(keyOf(entry)), "上下文不应出现重复条目");
        }

        JsonNode next = page(operator, "question", questionId, "newer",
                keyOf(window.get(window.size() - 1)), 2);
        assertEquals(2, next.path("entries").size());
        assertEquals(2, next.path("remaining").asInt());
        for (JsonNode entry : next.path("entries")) {
            assertTrue(seen.add(keyOf(entry)), "上下文不应出现重复条目");
        }

        next = page(operator, "question", questionId, "newer",
                keyOf(next.path("entries").get(next.path("entries").size() - 1)), 2);
        assertEquals(2, next.path("entries").size());
        assertEquals(0, next.path("remaining").asInt());
        for (JsonNode entry : next.path("entries")) {
            assertTrue(seen.add(keyOf(entry)), "上下文不应出现重复条目");
        }
        assertEquals(6, seen.size(), "问题+根回答+两轮追问回复应全部可达");

        JsonNode atEnd = page(operator, "question", questionId, "newer", keyOf(next.path("entries").get(1)), 2);
        assertEquals(0, atEnd.path("entries").size());
        assertEquals(0, atEnd.path("remaining").asInt());

        JsonNode followupDetail = detail(operator, "followup", followup1);
        JsonNode followupWindow = followupDetail.path("context");
        assertEquals(3, followupWindow.size(), "中间目标应返回上一条+当前+下一条");
        assertEquals(1, followupDetail.path("contextBeforeRemaining").asInt());
        assertEquals(2, followupDetail.path("contextAfterRemaining").asInt());
        assertFalse(followupWindow.get(0).path("target").asBoolean());
        assertTrue(followupWindow.get(1).path("target").asBoolean());
        assertEquals("followup", followupWindow.get(1).path("kind").asText());
        assertEquals(followup1, followupWindow.get(1).path("id").asLong());
        assertFalse(followupWindow.get(2).path("target").asBoolean());

        JsonNode older = page(operator, "followup", followup1, "older", keyOf(followupWindow.get(0)), 2);
        assertEquals(1, older.path("entries").size());
        assertEquals(0, older.path("remaining").asInt());
        assertEquals("question", older.path("entries").get(0).path("kind").asText());

        mockMvc.perform(get("/api/v1/admin/moderation/question/{id}/context/older", questionId)
                        .param("cursor", "answer:999999")
                        .session(operator))
                .andExpect(status().isBadRequest());

        assertTrue(rootAnswerId > 0 && reply1 > 0 && reply2 > 0);
    }
}