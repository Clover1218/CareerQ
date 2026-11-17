package com.careerq.feed;

import com.careerq.dao.TagDao;
import com.careerq.service.TagAffinityService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FeedFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TagAffinityService tagAffinityService;

    @Autowired
    private TagDao tagDao;

    private MockHttpSession register(String phone) throws Exception {
        return registerWithId(phone).session();
    }

    private record TestSession(long userId, MockHttpSession session) {
    }

    private TestSession registerWithId(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", "推荐流测试"))))
                .andExpect(status().isOk())
                .andReturn();
        long userId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("user").path("id").asLong();
        return new TestSession(userId, (MockHttpSession) result.getRequest().getSession(false));
    }

    private long createQuestion(MockHttpSession session, String title, String tag) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions").with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "body", "推荐流测试问题正文",
                                "tags", List.of(tag),
                                "scope", "public"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"))
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

    private JsonNode feed(MockHttpSession session, String query) throws Exception {
        var request = get("/api/v1/feed/questions" + query);
        if (session != null) {
            request = request.session(session);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode itemOf(JsonNode page, long questionId) {
        for (JsonNode item : page.path("items")) {
            if (item.path("id").asLong() == questionId) {
                return item;
            }
        }
        return null;
    }

    private List<String> matchedTags(JsonNode item) {
        List<String> names = new ArrayList<>();
        item.path("matchedTags").forEach(node -> names.add(node.asText()));
        return names;
    }

    @Test
    void guestFeedReturnsColdStartItemsWithoutMatchedTags() throws Exception {
        MockHttpSession creator = register("13900000601");
        long questionId = createQuestion(creator, "游客也能看到的推荐问题", "简历");

        JsonNode page = feed(null, "?limit=50");
        JsonNode item = itemOf(page, questionId);

        assertThat(item).isNotNull();
        assertThat(item.path("matchedTags").isArray()).isTrue();
        assertThat(matchedTags(item)).isEmpty();
    }

    @Test
    void likeRaisesTagAffinityAndExplainsRecommendation() throws Exception {
        MockHttpSession creator = register("13900000602");
        long likedId = createQuestion(creator, "简历改写的推荐流问题", "简历");
        long otherId = createQuestion(creator, "考研方向的推荐流问题", "考研");
        TestSession viewer = registerWithId("13900000603");

        postInteraction(viewer.session(), "question", likedId, "like");

        long tagId = tagDao.findBySlug("简历").orElseThrow().getId();
        Map<Long, Double> affinity = tagAffinityService.effectiveScores(viewer.userId(), LocalDateTime.now());
        assertThat(affinity.get(tagId)).isBetween(0.9, 1.0 + 1e-6);

        JsonNode page = feed(viewer.session(), "?limit=50");
        assertThat(matchedTags(itemOf(page, likedId))).containsExactly("简历");
        assertThat(matchedTags(itemOf(page, otherId))).isEmpty();
    }

    @Test
    void unlikeClearsAffinityAndRecommendationReason() throws Exception {
        MockHttpSession creator = register("13900000604");
        long questionId = createQuestion(creator, "取消点赞后的推荐流问题", "面试准备");
        TestSession viewer = registerWithId("13900000605");

        postInteraction(viewer.session(), "question", questionId, "favorite");
        long tagId = tagDao.findBySlug("面试准备").orElseThrow().getId();
        assertThat(tagAffinityService.effectiveScores(viewer.userId(), LocalDateTime.now())).containsKey(tagId);

        deleteInteraction(viewer.session(), "question", questionId, "favorite");

        assertThat(tagAffinityService.effectiveScores(viewer.userId(), LocalDateTime.now())).doesNotContainKey(tagId);
        JsonNode page = feed(viewer.session(), "?limit=50");
        assertThat(matchedTags(itemOf(page, questionId))).isEmpty();
    }

    @Test
    void dislikeHidesQuestionAndUndoRestoresIt() throws Exception {
        MockHttpSession creator = register("13900000606");
        long questionId = createQuestion(creator, "不感兴趣后隐藏的推荐流问题", "群面");
        MockHttpSession viewer = register("13900000607");

        postInteraction(viewer, "question", questionId, "dislike");

        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewer.disliked").value(true));
        assertThat(itemOf(feed(viewer, "?limit=50"), questionId)).isNull();

        deleteInteraction(viewer, "question", questionId, "dislike");

        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(viewer))
                .andExpect(jsonPath("$.data.viewer.disliked").value(false));
        assertThat(itemOf(feed(viewer, "?limit=50"), questionId)).isNotNull();
    }

    @Test
    void dislikeIsOnlySupportedOnQuestions() throws Exception {
        MockHttpSession session = register("13900000608");
        long questionId = createQuestion(session, "不喜欢仅支持问题", "单面");

        mockMvc.perform(post("/api/v1/interactions").with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "post", "targetId", questionId, "type", "dislike"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void cursorPaginationHasNoDuplicates() throws Exception {
        MockHttpSession creator = register("13900000609");
        for (int i = 0; i < 4; i++) {
            createQuestion(creator, "分页推荐流问题 " + i, "秋招");
        }

        JsonNode first = feed(null, "?limit=2");
        assertThat(first.path("items")).hasSize(2);
        String cursor = first.path("nextCursor").asText();
        assertThat(cursor).isNotBlank();

        JsonNode second = feed(null, "?limit=2&cursor=" + cursor);
        assertThat(second.path("items")).hasSize(2);

        Set<Long> ids = new HashSet<>();
        List<Long> all = new ArrayList<>();
        first.path("items").forEach(item -> all.add(item.path("id").asLong()));
        second.path("items").forEach(item -> all.add(item.path("id").asLong()));
        all.forEach(ids::add);
        assertThat(ids).hasSize(all.size());
    }

    @Test
    void invalidCursorIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/feed/questions").param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
