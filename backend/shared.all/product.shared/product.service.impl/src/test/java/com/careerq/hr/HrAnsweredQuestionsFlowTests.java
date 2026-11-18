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

    private MockHttpSession loginAsHr(String phone) throws Exception {
        MockHttpSession session = login(phone);
        long userId = objectMapper.readTree(mockMvc.perform(get("/api/v1/me").session(session))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("data").path("id").asLong();
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
                .andExpect(jsonPath("$.data.status").value("published"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
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
        mockMvc.perform(post("/api/v1/questions/{id}/followups", olderQuestion).session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "HR 不应提交追问"))))
                .andExpect(status().isForbidden());
        answer(hr, olderQuestion, "这是 HR 对较早问题的回答。");
        answer(hr, newerQuestion, "这是 HR 对较新问题的回答。");
        MockHttpSession anotherHr = loginAsHr("13800000883");
        answer(anotherHr, anotherHrQuestion, "这是另一位 HR 的回答。");

        mockMvc.perform(post("/api/v1/questions/{id}/followups", olderQuestion).session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "能否再举一个例子？"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/hr/answered-questions").session(hr).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].question.id").value(newerQuestion))
                .andExpect(jsonPath("$.data.items[0].myAnswer.body").value("这是 HR 对较新问题的回答。"))
                .andExpect(jsonPath("$.data.items[0].myAnswer.status").value("published"))
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
    void answeredQuestionHistoryIsHrOnly() throws Exception {
        mockMvc.perform(get("/api/v1/hr/answered-questions"))
                .andExpect(status().isUnauthorized());

        MockHttpSession student = login("13800000884");
        mockMvc.perform(get("/api/v1/hr/answered-questions").session(student))
                .andExpect(status().isForbidden());
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
