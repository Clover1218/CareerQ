package com.careerq.question;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 同一 HR 线程内的发言节奏：题主追问与 HR 回复严格交替。
 */
@SpringBootTest
@AutoConfigureMockMvc
class FollowupSequenceFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession login(String phone, String role) throws Exception {
        Map<String, Object> reg = new java.util.HashMap<>();
        reg.put("phone", phone);
        reg.put("code", "123456");
        reg.put("nickname", "节奏测试" + role);
        reg.put("role", role);
        if ("hr".equals(role)) {
            reg.put("company", "节奏测试公司");
        }
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();
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

    private long createQuestion(MockHttpSession student) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions").session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "发言节奏测试问题",
                                "body", "用于测试追问与回复的交替限制",
                                "tags", List.of("实习"),
                                "scope", "public"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long rootAnswer(MockHttpSession hr, long questionId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "建议先梳理项目里的优先级。"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long followUp(MockHttpSession student, long questionId, long hrId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions/{id}/followups", questionId)
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("hrId", hrId, "body", body))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long reply(MockHttpSession hr, long questionId, long followupId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions/{id}/followups/{fid}/answers", questionId, followupId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "可以先挑一个最能体现能力点的案例。"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    @Test
    void studentMustWaitForHrReplyBeforeNextFollowup() throws Exception {
        MockHttpSession student = login("13800005001", "student");
        MockHttpSession hr = login("13800005002", "hr");
        long questionId = createQuestion(student);
        long hrId = currentUserId(hr);

        rootAnswer(hr, questionId);
        long followupId = followUp(student, questionId, hrId, "追问：先做项目还是先刷题？");

        mockMvc.perform(post("/api/v1/questions/{id}/followups", questionId)
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "hrId", hrId, "body", "追问：那实习经历要怎么写？"))))
                .andExpect(status().isConflict());

        reply(hr, questionId, followupId);

        followUp(student, questionId, hrId, "追问：那实习经历要怎么写？");
    }

    @Test
    void hrCanOnlySubmitOneRootAnswer() throws Exception {
        MockHttpSession student = login("13800005003", "student");
        MockHttpSession hr = login("13800005004", "hr");
        long questionId = createQuestion(student);

        rootAnswer(hr, questionId);

        mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "补充一条主回答。"))))
                .andExpect(status().isConflict());
    }

    @Test
    void hrMustReplyToLatestUnansweredFollowup() throws Exception {
        MockHttpSession student = login("13800005005", "student");
        MockHttpSession hr = login("13800005006", "hr");
        long questionId = createQuestion(student);
        long hrId = currentUserId(hr);

        rootAnswer(hr, questionId);
        long firstFollowup = followUp(student, questionId, hrId, "追问一：简历要多长？");
        reply(hr, questionId, firstFollowup);

        mockMvc.perform(post("/api/v1/questions/{id}/followups/{fid}/answers", questionId, firstFollowup)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "再补一句。"))))
                .andExpect(status().isConflict());

        long secondFollowup = followUp(student, questionId, hrId, "追问二：面试怎么准备？");

        mockMvc.perform(post("/api/v1/questions/{id}/followups/{fid}/answers", questionId, firstFollowup)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "回复旧追问。"))))
                .andExpect(status().isConflict());

        reply(hr, questionId, secondFollowup);
    }

    @Test
    void withdrawnMessagesReleaseTheTurn() throws Exception {
        MockHttpSession student = login("13800005007", "student");
        MockHttpSession hr = login("13800005008", "hr");
        long questionId = createQuestion(student);
        long hrId = currentUserId(hr);

        rootAnswer(hr, questionId);
        long firstFollowup = followUp(student, questionId, hrId, "追问一：要不要投大厂？");
        mockMvc.perform(post("/api/v1/questions/{qid}/followups/{fid}/withdraw", questionId, firstFollowup)
                        .session(student).with(csrf()))
                .andExpect(status().isOk());

        long secondFollowup = followUp(student, questionId, hrId, "追问二：要不要投大厂？");
        long replyId = reply(hr, questionId, secondFollowup);
        mockMvc.perform(post("/api/v1/answers/{id}/withdraw", replyId)
                        .session(hr).with(csrf()))
                .andExpect(status().isOk());

        // 撤销的回答不占用轮次：HR 可以重新回复同一条追问
        reply(hr, questionId, secondFollowup);
        followUp(student, questionId, hrId, "追问三：那什么时候投合适？");
    }
}
