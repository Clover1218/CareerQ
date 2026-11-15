package com.careerq.privateflow;

import com.careerq.dataobject.PublicPostDO;
import com.careerq.dao.PublicPostDao;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
    private UserDao userDao;

    @Autowired
    private PublicPostDao publicPostDao;

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

    private long userId(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/me").session(session)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private MockHttpSession loginWithRole(String phone, String role) throws Exception {
        MockHttpSession session = login(phone);
        long id = userId(session);
        UserDO user = userDao.findById(id).orElseThrow();
        user.setRole(role);
        userDao.updateRole(user);
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
                .andExpect(jsonPath("$.data.status").value("pending_human"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private void decide(MockHttpSession operator, String target, long id, Map<String, ?> request) throws Exception {
        mockMvc.perform(post("/api/v1/admin/moderation/{target}/{id}/decide", target, id)
                        .session(operator).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void privateQuestionEndToEnd() throws Exception {
        MockHttpSession student = login("13800000201");
        long questionId = createPrivateQuestion(student);

        // 匿名与未指派 HR 不可见
        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isForbidden());

        MockHttpSession otherHr = loginWithRole("13800000202", "hr");
        MockHttpSession operator = loginWithRole("13800000206", "operator");
        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(otherHr))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(otherHr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "未认领就回答"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/hr/private-pool").session(otherHr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem((int) questionId))));
        mockMvc.perform(post("/api/v1/hr/questions/{id}/claim", questionId).session(otherHr).with(csrf()))
                .andExpect(status().isConflict());
        decide(operator, "question", questionId, Map.of("decision", "approve"));

        // 审核通过后才能认领。
        mockMvc.perform(get("/api/v1/hr/private-pool").session(otherHr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].author.nickname").value("匿名同学"));

        // 认领
        mockMvc.perform(post("/api/v1/hr/questions/{id}/claim", questionId).session(otherHr).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("assigned"));

        // 重复认领 → 409
        MockHttpSession thirdHr = loginWithRole("13800000203", "hr");
        mockMvc.perform(post("/api/v1/hr/questions/{id}/claim", questionId).session(thirdHr).with(csrf()))
                .andExpect(status().isConflict());

        // 学生与 HR 追问
        MvcResult followup = mockMvc.perform(post("/api/v1/questions/{id}/followups", questionId)
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "那应该说真实原因还是包装一下？"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending_human"))
                .andReturn();
        long followupId = objectMapper.readTree(followup.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(get("/api/v1/questions/{id}/followups", questionId).session(otherHr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        decide(operator, "followup", followupId, Map.of("decision", "approve"));
        mockMvc.perform(get("/api/v1/questions/{id}/followups", questionId).session(otherHr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("published"));

        // 已认领 HR 回答
        MvcResult answerResult = mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(otherHr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "如实说明空窗期做了什么，强调这段时间的学习与产出。",
                                "lengthType", "detailed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending_human"))
                .andReturn();
        long answerId = objectMapper.readTree(answerResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        assertTrue(answerId > 0);

        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followups.length()").value(1))
                .andExpect(jsonPath("$.data.answers.length()").value(0));
        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(otherHr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers[0].status").value("pending_human"));
        mockMvc.perform(post("/api/v1/questions/{id}/close", questionId).session(student).with(csrf()))
                .andExpect(status().isConflict());
        decide(operator, "answer", answerId, Map.of("decision", "approve"));
        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answers[0].status").value("published"))
                .andExpect(jsonPath("$.data.question.answerCount").value(1));

        mockMvc.perform(post("/api/v1/questions/{id}/close", questionId).session(student).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("public_pending"));

        PublicPostDO post = publicPostDao.findByOriginalQuestionId(questionId).orElseThrow();
        assertEquals("pending_human", post.getStatus());
        assertTrue(post.isAnonymized());
        assertEquals("如实说明空窗期做了什么，强调这段时间的学习与产出。", post.getHrAnswer());
        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", not(hasItem(post.getId().intValue()))));
        decide(operator, "public_post", post.getId(), Map.of("decision", "reject"));
        mockMvc.perform(post("/api/v1/questions/{id}/publish", questionId).session(student).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("public_pending"));
        assertEquals(post.getId(), publicPostDao.findByOriginalQuestionId(questionId).orElseThrow().getId());
        decide(operator, "public_post", post.getId(), Map.of("decision", "approve", "publicCopy", Map.of(
                "title", "求职空窗期怎么解释",
                "questionBody", "面试时如何说明职业空窗期？",
                "hrAnswer", "说明这段时间的学习与产出。")));
        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].title", hasItem("求职空窗期怎么解释")))
                .andExpect(jsonPath("$.data[*].hrAnswer", hasItem("说明这段时间的学习与产出。")));
        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.status").value("public_archived"));

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
