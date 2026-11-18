package com.careerq.question;

import com.careerq.dataobject.UserDO;
import com.careerq.dao.UserDao;
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
        MvcResult result = mockMvc.perform(post("/api/v1/questions")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "实习面试一般会问什么？",
                                "body", "想了解常见的面试问题与准备方式。",
                                "tags", List.of("实习", "面试准备"),
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
        mockMvc.perform(get("/api/v1/hr/answered-questions").session(hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(questionId));

        MockHttpSession anotherHr = loginAsHr("13800000110");
        mockMvc.perform(get("/api/v1/hr/answered-questions").session(anotherHr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        mockMvc.perform(get("/api/v1/hr/answered-questions").session(student))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrCannotCreateQuestion() throws Exception {
        MockHttpSession hr = loginAsHr("13800000109");

        mockMvc.perform(post("/api/v1/questions")
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "HR 不应创建学生问题",
                                "body", "HR 工作台只提供回答相关能力。",
                                "tags", List.of("权限"),
                                "scope", "public"))))
                .andExpect(status().isForbidden());
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
    void authorCanSeeOwnQuestionWhilePendingReviewButOthersCannot() throws Exception {
        MockHttpSession student = login("13800000111");
        MvcResult created = mockMvc.perform(post("/api/v1/questions")
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "实习沟通问题",
                                "body", "可以加微信聊一下实习机会吗？",
                                "tags", List.of("实习"),
                                "scope", "public"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending_human"))
                .andReturn();
        long questionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(login("13800000112")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.status").value("pending_human"))
                .andExpect(jsonPath("$.data.question.title").value("实习沟通问题"));
    }

}
