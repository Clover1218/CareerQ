package com.careerq.question;

import com.careerq.user.User;
import com.careerq.user.UserRepository;
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
    private UserRepository userRepository;

    private MockHttpSession login(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
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
        User user = userRepository.findById(userId).orElseThrow();
        user.setRole("hr");
        userRepository.save(user);
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

        mockMvc.perform(post("/api/v1/interactions")
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "answer",
                                "targetId", answerId,
                                "type", "useful"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers[0].usefulCount").value(1));

        mockMvc.perform(get("/api/v1/hr/question-pool").session(hr))
                .andExpect(status().isOk());
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
    void privateQuestionOnlyVisibleToAuthor() throws Exception {
        MockHttpSession student = login("13800000104");
        long questionId = createQuestion(student, "private");

        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.scope").value("private"));
    }
}
