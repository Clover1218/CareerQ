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
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        return createQuestion(session, title, List.of(tag));
    }

    private long createQuestion(MockHttpSession session, String title, List<String> tags) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions").with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "body", "推荐流测试问题正文",
                                "tags", tags,
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

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.path("items").forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }

    private void expectBadRequest(String query) throws Exception {
        mockMvc.perform(get("/api/v1/feed/questions" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void keywordMatchesTitleAndBodyCaseInsensitively() throws Exception {
        MockHttpSession creator = register("13900000701");
        long titleHit = createQuestion(creator, "Keyword701Token 标题问题", "filter701a");
        long bodyHit = createQuestion(creator, "正文命中的问题", "filter701a");
        long other = createQuestion(creator, "无关问题", "filter701b");
        jdbcTemplate.update("update questions set body = ? where id = ?",
                "这里包含 Keyword701Token 的正文", bodyHit);

        JsonNode page = feed(null, "?q=keyword701token&tags=filter701a&sort=latest&limit=50");

        assertThat(itemOf(page, titleHit)).isNotNull();
        assertThat(itemOf(page, bodyHit)).isNotNull();
        assertThat(itemOf(page, other)).isNull();
    }

    @Test
    void multipleTagsMatchAllWhenAndAndAnyWhenOr() throws Exception {
        MockHttpSession creator = register("13900000702");
        long dual = createQuestion(creator, "双标签问题", List.of("filter702a", "filter702b"));
        long single = createQuestion(creator, "单标签问题", List.of("filter702a"));

        JsonNode andPage = feed(null, "?tags=filter702a,filter702b&tagMode=and&sort=latest&limit=50");
        assertThat(itemOf(andPage, dual)).isNotNull();
        assertThat(itemOf(andPage, single)).isNull();

        JsonNode orPage = feed(null, "?tags=filter702a,filter702b&tagMode=or&sort=latest&limit=50");
        assertThat(itemOf(orPage, dual)).isNotNull();
        assertThat(itemOf(orPage, single)).isNotNull();
    }

    @Test
    void timeWindowAndCustomRangeFilterCreatedAt() throws Exception {
        MockHttpSession creator = register("13900000703");
        long recent = createQuestion(creator, "时间窗近期问题", "filter703a");
        long old = createQuestion(creator, "时间窗旧问题", "filter703a");
        jdbcTemplate.update("update questions set created_at = ? where id = ?",
                LocalDateTime.now().minusDays(10), old);

        JsonNode dayPage = feed(null, "?tags=filter703a&period=day&sort=latest&limit=50");
        assertThat(itemOf(dayPage, recent)).isNotNull();
        assertThat(itemOf(dayPage, old)).isNull();

        JsonNode weekPage = feed(null, "?tags=filter703a&period=week&sort=latest&limit=50");
        assertThat(itemOf(weekPage, old)).isNull();

        JsonNode monthPage = feed(null, "?tags=filter703a&period=month&sort=latest&limit=50");
        assertThat(itemOf(monthPage, recent)).isNotNull();
        assertThat(itemOf(monthPage, old)).isNotNull();

        JsonNode customPage = feed(null, "?tags=filter703a&from="
                + java.time.LocalDate.now().minusDays(30) + "&to=" + java.time.LocalDate.now().plusDays(1)
                + "&sort=latest&limit=50");
        assertThat(itemOf(customPage, recent)).isNotNull();
        assertThat(itemOf(customPage, old)).isNotNull();

        JsonNode futurePage = feed(null, "?tags=filter703a&from=2099-01-01&sort=latest&limit=50");
        assertThat(futurePage.path("items")).isEmpty();

        expectBadRequest("?tags=filter703a&from=2099-01-02&to=2099-01-01");
        expectBadRequest("?tags=filter703a&from=2020-01-01&to=2099-12-31");
    }

    @Test
    void latestSortReturnsNewestFirst() throws Exception {
        MockHttpSession creator = register("13900000704");
        long first = createQuestion(creator, "最新排序问题一", "filter704a");
        long second = createQuestion(creator, "最新排序问题二", "filter704a");

        List<Long> ordered = ids(feed(null, "?tags=filter704a&sort=latest&limit=50"));

        assertThat(ordered.indexOf(second)).isLessThan(ordered.indexOf(first));
    }

    @Test
    void hotSortRanksHigherEngagementFirst() throws Exception {
        MockHttpSession creator = register("13900000705");
        long plain = createQuestion(creator, "热门排序普通问题", "filter705a");
        long popular = createQuestion(creator, "热门排序高互动问题", "filter705a");
        MockHttpSession viewer = register("13900000706");
        postInteraction(viewer, "question", popular, "favorite");

        List<Long> ordered = ids(feed(null, "?tags=filter705a&sort=hot&limit=50"));

        assertThat(ordered.indexOf(popular)).isLessThan(ordered.indexOf(plain));
    }

    @Test
    void cursorIsRejectedAfterFilterOrSortChanges() throws Exception {
        MockHttpSession creator = register("13900000707");
        createQuestion(creator, "游标问题一", "filter707a");
        createQuestion(creator, "游标问题二", "filter707a");
        createQuestion(creator, "游标问题三", "filter707b");

        JsonNode first = feed(null, "?tags=filter707a&sort=latest&limit=1");
        String cursor = first.path("nextCursor").asText();
        assertThat(cursor).isNotBlank();

        expectBadRequest("?tags=filter707b&sort=latest&limit=1&cursor=" + cursor);
        expectBadRequest("?tags=filter707a&sort=hot&limit=1&cursor=" + cursor);
    }

    @Test
    void periodFilterKeepsCursorValidAcrossPages() throws Exception {
        MockHttpSession creator = register("13900000708");
        for (int i = 0; i < 3; i++) {
            createQuestion(creator, "时间窗分页问题 " + i, "filter708a");
        }

        JsonNode latestFirst = feed(null, "?tags=filter708a&period=day&sort=latest&limit=1");
        String latestCursor = latestFirst.path("nextCursor").asText();
        assertThat(latestCursor).isNotBlank();
        JsonNode latestSecond = feed(null,
                "?tags=filter708a&period=day&sort=latest&limit=1&cursor=" + latestCursor);
        assertThat(latestSecond.path("items")).hasSize(1);

        JsonNode hotFirst = feed(null, "?tags=filter708a&period=day&sort=hot&limit=1");
        String hotCursor = hotFirst.path("nextCursor").asText();
        assertThat(hotCursor).isNotBlank();
        JsonNode hotSecond = feed(null,
                "?tags=filter708a&period=day&sort=hot&limit=1&cursor=" + hotCursor);
        assertThat(hotSecond.path("items")).hasSize(1);
    }

    @Test
    void invalidFilterParamsAreRejected() throws Exception {
        expectBadRequest("?sort=bogus");
        expectBadRequest("?tagMode=xor");
        expectBadRequest("?period=year");
        expectBadRequest("?q=" + "a".repeat(51));
        StringBuilder tooMany = new StringBuilder("?tags=");
        for (int i = 1; i <= 11; i++) {
            if (i > 1) {
                tooMany.append(',');
            }
            tooMany.append("tag").append(i);
        }
        expectBadRequest(tooMany.toString());
    }
}
