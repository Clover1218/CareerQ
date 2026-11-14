package com.careerq.privateflow;

import com.careerq.publicpost.PublicPost;
import com.careerq.publicpost.PublicPostRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PrivateFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PublicPostRepository publicPostRepository;

    private MockHttpSession login(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long userId(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/me").session(session)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private MockHttpSession loginAsHr(String phone) throws Exception {
        MockHttpSession session = login(phone);
        long id = userId(session);
        User user = userRepository.findById(id).orElseThrow();
        user.setRole("hr");
        userRepository.save(user);
        return login(phone);
    }

    private long createPrivateQuestion(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "简历上的空窗期该怎么解释？",
                                "body", "有一段半年的空窗，面试被问到时不知道怎么说。",
                                "tags", List.of("简历", "面试准备"),
                                "scope", "private"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("open"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    @Test
    void privateQuestionEndToEnd() throws Exception {
        MockHttpSession student = login("13800000201");
        long questionId = createPrivateQuestion(student);

        // 匿名与未指派 HR 不可见
        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isForbidden());

        MockHttpSession otherHr = loginAsHr("13800000202");
        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(otherHr))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(otherHr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "未认领就回答"))))
                .andExpect(status().isForbidden());

        // HR 池可见（作者匿名化）
        mockMvc.perform(get("/api/v1/hr/private-pool").session(otherHr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].author.nickname").value("匿名同学"));

        // 认领
        mockMvc.perform(post("/api/v1/hr/questions/{id}/claim", questionId).session(otherHr).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("assigned"));

        // 重复认领 → 409
        MockHttpSession thirdHr = loginAsHr("13800000203");
        mockMvc.perform(post("/api/v1/hr/questions/{id}/claim", questionId).session(thirdHr).with(csrf()))
                .andExpect(status().isConflict());

        // 学生与 HR 追问
        mockMvc.perform(post("/api/v1/questions/{id}/followups", questionId)
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "那应该说真实原因还是包装一下？"))))
                .andExpect(status().isOk());

        // 已认领 HR 回答
        MvcResult answerResult = mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(otherHr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "如实说明空窗期做了什么，强调这段时间的学习与产出。",
                                "lengthType", "detailed"))))
                .andExpect(status().isOk())
                .andReturn();
        long answerId = objectMapper.readTree(answerResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        assertTrue(answerId > 0);

        // 详情包含追问
        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followups.length()").value(1))
                .andExpect(jsonPath("$.data.answers.length()").value(1));

        // 关闭
        mockMvc.perform(post("/api/v1/questions/{id}/close", questionId).session(student).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("closed"));

        // 转公开：生成待审核 public_post
        mockMvc.perform(post("/api/v1/questions/{id}/publish", questionId).session(student).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("public_pending"));

        PublicPost post = publicPostRepository.findByOriginalQuestionId(questionId).orElseThrow();
        assertEquals("pending_human", post.getStatus());
        assertTrue(post.isAnonymized());
        assertEquals("如实说明空窗期做了什么，强调这段时间的学习与产出。", post.getHrAnswer());

        // 未关闭/非作者不能公开
        mockMvc.perform(post("/api/v1/questions/{id}/publish", questionId).session(otherHr).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void closeRequiresAuthorAndPrivateScope() throws Exception {
        MockHttpSession student = login("13800000204");
        long privateId = createPrivateQuestion(student);
        MockHttpSession other = login("13800000205");
        mockMvc.perform(post("/api/v1/questions/{id}/close", privateId).session(other).with(csrf()))
                .andExpect(status().isForbidden());

        MvcResult publicResult = mockMvc.perform(post("/api/v1/questions")
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "公开问题", "body", "公开问题的描述", "scope", "public"))))
                .andExpect(status().isOk())
                .andReturn();
        long publicId = objectMapper.readTree(publicResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(post("/api/v1/questions/{id}/close", publicId).session(student).with(csrf()))
                .andExpect(status().isConflict());
    }
}
