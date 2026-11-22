package com.careerq.hr;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HrAnsweredQuestionsFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserDao userDao;

    private MockHttpSession login(String phone) throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", "回答记录测试"))))
                .andReturn();
        if (registration.getResponse().getStatus() != 200 && registration.getResponse().getStatus() != 409) {
            throw new IllegalStateException("注册失败，status=" + registration.getResponse().getStatus());
        }
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long currentUserId(MockHttpSession session) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/me").session(session))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
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

    private long createQuestion(MockHttpSession student, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions").session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "body", "用于验证 HR 回答记录和学生追问的公开问题。",
                                "tags", List.of("回答记录测试"),
                                "scope", "public"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayStatus").value("PUBLISHED"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private long rootAnswerId(MockHttpSession viewer, long questionId, long hrId) throws Exception {
        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(viewer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        for (JsonNode answer : detail.path("answers")) {
            if (answer.path("author").path("id").asLong() == hrId && answer.path("followupId").isMissingNode()) {
                return answer.path("id").asLong();
            }
        }
        throw new IllegalStateException("缺少目标主回答");
    }

    private void answer(MockHttpSession hr, long questionId, String body) throws Exception {
        mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId).session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", body, "lengthType", "detailed"))))
                .andExpect(status().isOk());
    }

    @Test
    void hrCanPageOwnPublicAnswersAndSeeQuestionFollowupCount() throws Exception {
        MockHttpSession student = login("13800000881");
        long olderQuestion = createQuestion(student, "HR 回答记录较早问题");
        long newerQuestion = createQuestion(student, "HR 回答记录较新问题");
        long anotherHrQuestion = createQuestion(student, "另一位 HR 回答的问题");

        MockHttpSession hr = loginAsHr("13800000882");
        long hrId = currentUserId(hr);
        mockMvc.perform(post("/api/v1/questions/{id}/followups", olderQuestion).session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("hrId", hrId, "rootAnswerId", 1, "body", "HR 不应提交追问"))))
                .andExpect(status().isForbidden());
        answer(hr, olderQuestion, "这是 HR 对较早问题的回答。");
        answer(hr, newerQuestion, "这是 HR 对较新问题的回答。");
        MockHttpSession anotherHr = loginAsHr("13800000883");
        answer(anotherHr, anotherHrQuestion, "这是另一位 HR 的回答。");

        MvcResult answeredPublic = mockMvc.perform(get("/api/v1/hr/answered-public-questions")
                        .session(hr).param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andReturn();
        JsonNode answeredPublicItems = objectMapper.readTree(answeredPublic.getResponse().getContentAsString())
                .path("data").path("items");
        assertTrue(containsQuestion(answeredPublicItems, olderQuestion));
        assertTrue(containsQuestion(answeredPublicItems, newerQuestion));
        assertTrue(containsQuestion(answeredPublicItems, anotherHrQuestion),
                "已回答公开问题应包含其他 HR 的回答");

        MvcResult pending = mockMvc.perform(get("/api/v1/hr/question-pool").session(hr)
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pendingItems = objectMapper.readTree(pending.getResponse().getContentAsString())
                .path("data").path("items");
        assertFalse(containsQuestion(pendingItems, olderQuestion));
        assertFalse(containsQuestion(pendingItems, newerQuestion));
        assertFalse(containsQuestion(pendingItems, anotherHrQuestion));

        mockMvc.perform(post("/api/v1/questions/{id}/followups", olderQuestion).session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "hrId", hrId, "rootAnswerId", rootAnswerId(student, olderQuestion, hrId), "body", "能否再举一个例子？"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/hr/answered-questions").session(hr).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].question.id").value(newerQuestion))
                .andExpect(jsonPath("$.data.items[0].myAnswer.body").value("这是 HR 对较新问题的回答。"))
                .andExpect(jsonPath("$.data.items[0].myAnswer.displayStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.items[0].question.followupCount").value(0))
                .andExpect(jsonPath("$.data.nextCursor").value(newerQuestion));

        mockMvc.perform(get("/api/v1/hr/answered-questions").session(hr)
                        .param("limit", "1").param("cursor", Long.toString(newerQuestion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].question.id").value(olderQuestion))
                .andExpect(jsonPath("$.data.items[0].question.followupCount").value(1))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());

        MvcResult thread = mockMvc.perform(get("/api/v1/questions/{id}/thread", olderQuestion).session(hr))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(thread.getResponse().getContentAsString()).path("data").path("items");
        assertTrue(items.size() >= 2);
        boolean studentFollowupVisible = false;
        for (JsonNode item : items) {
            if ("followup".equals(item.path("kind").asText())
                    && "能否再举一个例子？".equals(item.path("body").asText())) {
                studentFollowupVisible = true;
                break;
            }
        }
        assertTrue(studentFollowupVisible);

        MvcResult ownHistory = mockMvc.perform(get("/api/v1/hr/answered-questions").session(hr)
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ownItems = objectMapper.readTree(ownHistory.getResponse().getContentAsString()).path("data").path("items");
        for (JsonNode item : ownItems) {
            assertFalse(item.path("question").path("id").asLong() == anotherHrQuestion);
        }
    }

    @Test
    void hrCanAnswerMainQuestionOrReplyToSpecificStudentFollowup() throws Exception {
        MockHttpSession student = login("13800000886");
        long questionId = createQuestion(student, "HR 可回复指定追问的问题");
        long otherQuestionId = createQuestion(student, "不能跨问题回复的追问");
        MockHttpSession hr = loginAsHr("13800000887");
        long hrId = currentUserId(hr);
        answer(hr, questionId, "这是对主问题的回答。");

        MvcResult followupResult = mockMvc.perform(post("/api/v1/questions/{id}/followups", questionId)
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "hrId", hrId, "rootAnswerId", rootAnswerId(student, questionId, hrId), "body", "可以再解释一下第二点吗？"))))
                .andExpect(status().isOk())
                .andReturn();
        long followupId = objectMapper.readTree(followupResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/questions/{id}/followups/{followupId}/answers", questionId, followupId)
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "学生不能用 HR 回复接口"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/questions/{id}/followups/{followupId}/answers", otherQuestionId, followupId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "不能跨问题回复"))))
                .andExpect(status().isNotFound());

        MockHttpSession otherHr = loginAsHr("13800000888");
        mockMvc.perform(post("/api/v1/questions/{id}/followups/{followupId}/answers", questionId, followupId)
                        .session(otherHr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "其他 HR 不能回复这条追问"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/questions/{id}/followups/{followupId}/answers", questionId, followupId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "第二点的意思是先结合岗位要求准备案例。", "lengthType", "detailed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followupId").value(followupId));

        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.answerCount").value(2))
                .andExpect(jsonPath("$.data.answers.length()").value(2))
                .andExpect(jsonPath("$.data.answers[0].followupId").doesNotExist())
                .andExpect(jsonPath("$.data.answers[1].followupId").value(followupId));

        mockMvc.perform(get("/api/v1/questions/{id}/thread", questionId).session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.kind == 'followup_answer' && @.followupId == " + followupId + ")].body")
                        .value(org.hamcrest.Matchers.hasItem("第二点的意思是先结合岗位要求准备案例。")));

        mockMvc.perform(get("/api/v1/hr/answered-questions").session(hr).param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].myAnswer.followupId").value((int) followupId));

        mockMvc.perform(post(
                                "/api/v1/questions/{id}/followups/{followupId}/answers", questionId, followupId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "请加微信联系我继续沟通。"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers.length()").value(2));
        mockMvc.perform(get("/api/v1/questions/{id}/thread", questionId).session(hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3));
    }

    @Test
    void answeredQuestionHistoryIsHrOnly() throws Exception {
        mockMvc.perform(get("/api/v1/hr/answered-questions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/hr/answered-public-questions"))
                .andExpect(status().isUnauthorized());

        MockHttpSession student = login("13800000884");
        mockMvc.perform(get("/api/v1/hr/answered-questions").session(student))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/hr/answered-public-questions").session(student))
                .andExpect(status().isForbidden());
    }

    @Test
    void flaggedAnswerPublishesImmediatelyAndLeavesTodo() throws Exception {
        MockHttpSession student = login("13800000888");
        long questionId = createQuestion(student, "命中风险词的回答立即公开");
        MockHttpSession hr = loginAsHr("13800000889");

        mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId).session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "请加微信联系我"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayStatus").value("PUBLISHED"));

        MvcResult pending = mockMvc.perform(get("/api/v1/hr/question-pool").session(hr).param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn();
        assertFalse(containsQuestion(objectMapper.readTree(pending.getResponse().getContentAsString())
                .path("data").path("items"), questionId));

        MvcResult answered = mockMvc.perform(get("/api/v1/hr/answered-public-questions")
                        .session(hr).param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode entry = findQuestion(objectMapper.readTree(answered.getResponse().getContentAsString())
                .path("data").path("items"), questionId);
        assertTrue(entry.path("hasPendingAnswer").asBoolean());
        assertTrue(entry.path("question").path("answerCount").asInt() == 1,
                "L2 通过的回答应立即计入公开回答数");

        mockMvc.perform(get("/api/v1/hr/answered-questions").session(hr).param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].question.id").value(questionId))
                .andExpect(jsonPath("$.data.items[0].myAnswer.displayStatus").value("PUBLISHED"));
    }

    private boolean containsQuestion(JsonNode items, long questionId) {
        for (JsonNode item : items) {
            if (item.path("id").asLong() == questionId
                    || item.path("question").path("id").asLong() == questionId) {
                return true;
            }
        }
        return false;
    }

    private JsonNode findQuestion(JsonNode items, long questionId) {
        for (JsonNode item : items) {
            if (item.path("question").path("id").asLong() == questionId) {
                return item;
            }
        }
        throw new AssertionError("问题不存在于结果中: " + questionId);
    }

    @Test
    void emptyHistoryHasNoNextCursorAndLimitIsBounded() throws Exception {
        MockHttpSession hr = loginAsHr("13800000885");
        mockMvc.perform(get("/api/v1/hr/answered-questions").session(hr).param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }
}
