package com.careerq.me;

import com.careerq.dao.UserDao;
import com.careerq.dataobject.UserDO;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MeFlowTests {

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
                                "phone", phone, "code", "123456", "nickname", "我的主页用户"))))
                .andReturn();
        int code = reg.getResponse().getStatus();
        if (code != 200 && code != 409) {
            throw new IllegalStateException("注册失败，status=" + code);
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
        UserDO user = userDao.findById(currentUserId(session)).orElseThrow();
        user.setRole("hr");
        userDao.updateRole(user);
        return login(phone);
    }

    private long createQuestion(MockHttpSession session, String scope, String title, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "body", body,
                                "tags", List.of("我的问答测试"),
                                "scope", scope))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    @Test
    void myQuestionsListFiltersByScope() throws Exception {
        MockHttpSession student = login("13800000901");
        long publicId = createQuestion(student, "public", "我的公开提问", "公开问题正文");
        long privateId = createQuestion(student, "private", "我的私密提问", "私密问题正文");

        mockMvc.perform(get("/api/v1/me/questions").session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(privateId))
                .andExpect(jsonPath("$.data.items[0].status").value("pending_human"))
                .andExpect(jsonPath("$.data.items[1].id").value(publicId))
                .andExpect(jsonPath("$.data.items[1].status").value("published"))
                .andExpect(jsonPath("$.data.items[0].body").value("私密问题正文"));

        mockMvc.perform(get("/api/v1/me/questions").session(student).param("scope", "public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(publicId));

        mockMvc.perform(get("/api/v1/me/questions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statsCountReceivedAnswersAndLikes() throws Exception {
        MockHttpSession student = login("13800000902");
        long questionId = createQuestion(student, "public", "统计测试提问", "用于统计回答与点赞");

        MockHttpSession hr = loginAsHr("13800000903");
        MvcResult answerResult = mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "这是一条用于统计的回答。", "lengthType", "detailed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"))
                .andReturn();
        long answerId = objectMapper.readTree(answerResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/v1/interactions").session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "question", "targetId", questionId, "type", "like"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/interactions").session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "answer", "targetId", answerId, "type", "like"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/stats").session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionCount").value(1))
                .andExpect(jsonPath("$.data.receivedAnswerCount").value(1))
                .andExpect(jsonPath("$.data.authoredAnswerCount").value(0))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        mockMvc.perform(get("/api/v1/me/stats").session(hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authoredAnswerCount").value(1))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        mockMvc.perform(get("/api/v1/me/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authorCanViewOwnPendingPublicQuestion() throws Exception {
        MockHttpSession author = login("13800000904");
        long questionId = createQuestion(author, "public", "审核中的公开提问", "正文包含加微信，进入人工审核。");

        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.status").value("pending_human"))
                .andExpect(jsonPath("$.data.question.body").value("正文包含加微信，进入人工审核。"));

        MockHttpSession stranger = login("13800000905");
        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void profilePatchKeepsUnspecifiedFieldsAndExperiences() throws Exception {
        MockHttpSession session = login("13800000906");

        mockMvc.perform(put("/api/v1/me/profile")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "school", "原学校",
                                "major", "原专业",
                                "grade", "大二",
                                "experiences", List.of(
                                        Map.of("type", "internship", "title", "某公司", "role", "实习生"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experiences.length()").value(1));

        mockMvc.perform(patch("/api/v1/me/profile")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nickname", "新昵称",
                                "major", "软件工程"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.school").value("原学校"))
                .andExpect(jsonPath("$.data.major").value("软件工程"))
                .andExpect(jsonPath("$.data.grade").value("大二"))
                .andExpect(jsonPath("$.data.experiences.length()").value(1));

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));

        mockMvc.perform(patch("/api/v1/me/profile")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("experiences", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.experiences.length()").value(0));

        mockMvc.perform(patch("/api/v1/me/profile")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
