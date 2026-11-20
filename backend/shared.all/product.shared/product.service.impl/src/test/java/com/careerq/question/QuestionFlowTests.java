package com.careerq.question;

import com.careerq.dataobject.UserDO;
import com.careerq.dao.UserDao;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QuestionFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserDao userDao;

    private void registerIfNeeded(String phone) throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", "测试用户"))))
                .andReturn();
        int status = reg.getResponse().getStatus();
        if (status != 200 && status != 409) {
            throw new IllegalStateException("注册失败，status=" + status);
        }
    }
    private MockHttpSession login(String phone) throws Exception {
        registerIfNeeded(phone);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long currentUserId(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private MockHttpSession loginAsHr(String phone) throws Exception {
        MockHttpSession session = login(phone);
        long userId = currentUserId(session);
        UserDO user = userDao.findById(userId).orElseThrow();
        user.setRole("hr");
        userDao.updateRole(user);
        return login(phone);
    }

    private long createQuestion(MockHttpSession session, String scope) throws Exception {
        return createQuestion(session, scope, "实习面试一般会问什么？",
                "想了解常见的面试问题与准备方式。", List.of("实习", "面试准备"));
    }

    private long createQuestion(MockHttpSession session, String scope, String title, String body,
                                List<String> tags) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "body", body,
                                "tags", tags,
                                "scope", scope))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    @Test
    void publicQuestionCreateAnswerAndInteract() throws Exception {
        MockHttpSession student = login("13800000101");
        long questionId = createQuestion(student, "public");

        mockMvc.perform(get("/api/v1/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].id").exists());

        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.id").value(questionId))
                .andExpect(jsonPath("$.data.question.tags.length()").value(2))
                .andExpect(jsonPath("$.data.answers.length()").value(0));

        MockHttpSession hr = loginAsHr("13800000102");
        MvcResult answerResult = mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "常见问题包括自我介绍、项目经历、岗位理解，建议提前准备 STAR 案例。",
                                "lengthType", "detailed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"))
                .andReturn();
        long answerId = objectMapper.readTree(answerResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.answerCount").value(1))
                .andExpect(jsonPath("$.data.question.firstAnswerAt").isNotEmpty())
                .andExpect(jsonPath("$.data.answers.length()").value(1));

        String interaction = objectMapper.writeValueAsString(Map.of(
                "targetType", "answer", "targetId", answerId, "type", "useful"));
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/interactions")
                            .session(student).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(interaction))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers[0].usefulCount").value(1));

        mockMvc.perform(get("/api/v1/hr/question-pool").session(hr))
                .andExpect(status().isOk());
    }

    @Test
    void publicListFiltersByTagAcrossCursorPages() throws Exception {
        MockHttpSession student = login("13800000105");
        long older = createQuestion(student, "public");
        long newer = createQuestion(student, "public");

        mockMvc.perform(get("/api/v1/questions").param("tag", "实习").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(newer))
                .andExpect(jsonPath("$.data.nextCursor").value(newer));

        mockMvc.perform(get("/api/v1/questions").param("tag", "实习").param("limit", "1")
                        .param("cursor", Long.toString(newer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(older));

        mockMvc.perform(get("/api/v1/questions").param("tag", "不存在的标签"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void studentCannotAnswer() throws Exception {
        MockHttpSession student = login("13800000103");
        long questionId = createQuestion(student, "public");

        mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "我来回答"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void privateQuestionCreationRejected() throws Exception {
        MockHttpSession student = login("13800000104");
        mockMvc.perform(post("/api/v1/questions")
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "不支持的可见范围",
                                "body", "无效范围内容",
                                "tags", List.of("实习"),
                                "scope", "private"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void publicFeedReturnsBodyAndContentReturnsAuthor() throws Exception {
        MockHttpSession student = login("13800000106");
        String longBody = "这是一段用于验证全文展示的较长问题描述，不限制字数也不应被截断。".repeat(4);
        long questionId = createQuestion(student, "public", "公开问答全文展开测试", longBody,
                List.of("作品集"));

        mockMvc.perform(get("/api/v1/questions").param("tag", "作品集"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(questionId))
                .andExpect(jsonPath("$.data.items[0].title").value("公开问答全文展开测试"))
                .andExpect(jsonPath("$.data.items[0].excerpt").value(longBody))
                .andExpect(jsonPath("$.data.items[0].truncated").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].author").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].replyCount").value(0))
                .andExpect(jsonPath("$.data.items[0].favoriteCount").value(0));

        mockMvc.perform(get("/api/v1/questions/{id}/content", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.id").value(questionId))
                .andExpect(jsonPath("$.data.question.body").value(longBody))
                .andExpect(jsonPath("$.data.question.author.nickname").value("测试用户"))
                .andExpect(jsonPath("$.data.question.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.viewer.liked").value(false))
                .andExpect(jsonPath("$.data.viewer.favorited").value(false));

        mockMvc.perform(post("/api/v1/interactions").session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "question", "targetId", questionId, "type", "favorite"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/questions/{id}/content", questionId).session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.favoriteCount").value(1))
                .andExpect(jsonPath("$.data.viewer.favorited").value(true));

        mockMvc.perform(get("/api/v1/questions").param("tag", "作品集"))
                .andExpect(jsonPath("$.data.items[0].favoriteCount").value(1));

    }

    @Test
    void threadMergesHrAnswersAndFollowupsWithHrFlags() throws Exception {
        MockHttpSession student = login("13800000107");
        long questionId = createQuestion(student, "public", "回答流合并测试",
                "想了解回答与追问如何平级展示。", List.of("回答流测试"));

        mockMvc.perform(post("/api/v1/questions/{id}/followups", questionId)
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "想先了解一下准备周期。"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hr").value(false))
                .andExpect(jsonPath("$.data.author.nickname").value("测试用户"));

        MockHttpSession hr = loginAsHr("13800000108");
        MvcResult answerResult = mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "建议预留两到三周，先梳理项目再做模拟面试。",
                                "lengthType", "detailed"))))
                .andExpect(status().isOk())
                .andReturn();
        long answerId = objectMapper.readTree(answerResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/questions/{id}/followups", questionId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "也可以先做一次模拟面试评估基础。"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/interactions").session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "answer", "targetId", answerId, "type", "like"))))
                .andExpect(status().isOk());

        MvcResult threadResult = mockMvc.perform(get("/api/v1/questions/{id}/thread", questionId)
                        .session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answerCount").value(1))
                .andExpect(jsonPath("$.data.followupCount").value(1))
                .andExpect(jsonPath("$.data.replyCount").value(2))
                .andReturn();

        JsonNode data = objectMapper.readTree(threadResult.getResponse().getContentAsString()).path("data");
        JsonNode items = data.path("items");
        assertEquals(2, items.size());
        List<String> kinds = new ArrayList<>();
        int hrAnswerIndex = -1;
        int studentFollowupIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            kinds.add(item.path("kind").asText());
            assertTrue(item.path("author").path("nickname").asText().length() > 0);
            if ("hr_answer".equals(item.path("kind").asText())) {
                hrAnswerIndex = i;
                assertTrue(item.path("hr").asBoolean());
                assertEquals(answerId, item.path("id").asLong());
                assertTrue(item.path("liked").asBoolean());
            }
            if ("followup".equals(item.path("kind").asText())
                    && "测试用户".equals(item.path("author").path("nickname").asText())
                    && !item.path("hr").asBoolean()) {
                studentFollowupIndex = i;
            }
        }
        assertTrue(kinds.contains("hr_answer"));
        assertTrue(kinds.contains("followup"));
        assertTrue(hrAnswerIndex >= 0);
        assertTrue(studentFollowupIndex >= 0);
        assertEquals(1, kinds.stream().filter("followup"::equals).count());

        mockMvc.perform(get("/api/v1/questions/{id}/thread", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewer.liked").value(false));
    }
}
