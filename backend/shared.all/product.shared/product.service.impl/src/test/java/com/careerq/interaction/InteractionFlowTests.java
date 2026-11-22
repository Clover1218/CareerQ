package com.careerq.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InteractionFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession register(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", "互动测试"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long createQuestion(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions").with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "如何准备后端实习面试",
                                "body", "想了解准备方法和时间安排",
                                "tags", java.util.List.of("实习"),
                                "scope", "public"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayStatus").value("PUBLISHED"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private void postInteraction(MockHttpSession session, String targetType, long targetId, String type)
            throws Exception {
        mockMvc.perform(post("/api/v1/interactions").with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", targetType, "targetId", targetId, "type", type))))
                .andExpect(status().isOk());
    }

    private void deleteInteraction(MockHttpSession session, String targetType, long targetId, String type)
            throws Exception {
        mockMvc.perform(delete("/api/v1/interactions").with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", targetType, "targetId", targetId, "type", type))))
                .andExpect(status().isOk());
    }

    private JsonNode feedItem(MockHttpSession session, String sort, long questionId) throws Exception {
        var request = get("/api/v1/feed/questions").param("sort", sort).param("limit", "50")
                .param("q", "如何准备后端实习面试");
        if (session != null) request.session(session);
        var response = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        var items = objectMapper.readTree(response.getResponse().getContentAsString()).path("data").path("items");
        for (var item : items) if (item.path("id").asLong() == questionId) return item;
        throw new AssertionError("目标问题未出现在 " + sort + " 列表中");
    }

    @Test
    void feedRestoresSavedInteractionsAndIsolatesViewers() throws Exception {
        String phone = "13978000001";
        MockHttpSession student = register(phone);
        MockHttpSession other = register("13978000002");
        long questionId = createQuestion(student);
        postInteraction(student, "question", questionId, "like");
        postInteraction(student, "question", questionId, "favorite");
        var relogin = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk()).andReturn();
        student = (MockHttpSession) relogin.getRequest().getSession(false);
        for (String sort : java.util.List.of("latest", "hot", "recommend")) {
            var own = feedItem(student, sort, questionId);
            assertTrue(own.path("viewer").path("liked").asBoolean());
            assertTrue(own.path("viewer").path("favorited").asBoolean());
            assertEquals(1, own.path("likeCount").asInt());
            assertEquals(1, own.path("favoriteCount").asInt());
            for (MockHttpSession viewer : new MockHttpSession[]{other, null}) {
                var flags = feedItem(viewer, sort, questionId).path("viewer");
                assertFalse(flags.isMissingNode());
                assertFalse(flags.path("liked").asBoolean());
                assertFalse(flags.path("favorited").asBoolean());
            }
        }
        deleteInteraction(student, "question", questionId, "like");
        var afterUnlike = feedItem(student, "latest", questionId);
        assertFalse(afterUnlike.path("viewer").path("liked").asBoolean());
        assertTrue(afterUnlike.path("viewer").path("favorited").asBoolean());
        assertEquals(0, afterUnlike.path("likeCount").asInt());
        deleteInteraction(student, "question", questionId, "favorite");
        var afterCancel = feedItem(student, "latest", questionId);
        assertFalse(afterCancel.path("viewer").path("favorited").asBoolean());
        assertEquals(0, afterCancel.path("favoriteCount").asInt());
    }

    @Test
    void likeIsIdempotentAndCanBeCancelled() throws Exception {
        MockHttpSession session = register("13800000502");
        long questionId = createQuestion(session);

        postInteraction(session, "question", questionId, "like");
        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.likeCount").value(1))
                .andExpect(jsonPath("$.data.viewer.liked").value(true));

        // 重复点赞：幂等，不重复计数
        postInteraction(session, "question", questionId, "like");
        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(session))
                .andExpect(jsonPath("$.data.question.likeCount").value(1));

        deleteInteraction(session, "question", questionId, "like");
        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(session))
                .andExpect(jsonPath("$.data.question.likeCount").value(0))
                .andExpect(jsonPath("$.data.viewer.liked").value(false));
    }

    @Test
    void usefulAndFavoriteFlow() throws Exception {
        MockHttpSession session = register("13800000503");
        long questionId = createQuestion(session);

        postInteraction(session, "question", questionId, "useful");
        postInteraction(session, "question", questionId, "favorite");

        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.usefulCount").value(1))
                .andExpect(jsonPath("$.data.viewer.usefuled").value(true))
                .andExpect(jsonPath("$.data.viewer.favorited").value(true));

        mockMvc.perform(get("/api/v1/me/favorites").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].targetType").value("question"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(questionId))
                .andExpect(jsonPath("$.data.items[0].title").value("如何准备后端实习面试"));

        deleteInteraction(session, "question", questionId, "favorite");
        mockMvc.perform(get("/api/v1/me/favorites").session(session))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void favoritesRequireLogin() throws Exception {
        mockMvc.perform(get("/api/v1/me/favorites"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void retiredPostTargetIsRejected() throws Exception {
        MockHttpSession session = register("13800000504");
        long questionId = createQuestion(session);
        mockMvc.perform(post("/api/v1/interactions").with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "post", "targetId", questionId, "type", "useful"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
