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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回复（HR 回答）与追问的撤销：仅作者本人，重复撤销冲突。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReplyWithdrawFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession login(String phone, String role) throws Exception {
        Map<String, Object> reg = new java.util.HashMap<>();
        reg.put("phone", phone);
        reg.put("code", "123456");
        reg.put("nickname", "撤销测试" + role);
        reg.put("role", role);
        if ("hr".equals(role)) {
            reg.put("company", "测试公司");
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

    private long createQuestion(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "回复撤销测试问题",
                                "body", "用于测试回复与追问的撤销",
                                "tags", List.of("实习"),
                                "scope", "public"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long answer(MockHttpSession hr, long questionId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions/{id}/answers", questionId)
                        .session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "建议先梳理优先级。"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long currentUserId(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long followup(MockHttpSession student, long questionId, long hrId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions/{id}/followups", questionId)
                        .session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "hrId", hrId, "body", "追问：具体该怎么排序？"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    @Test
    void authorCanWithdrawOwnAnswerAndFollowup() throws Exception {
        MockHttpSession student = login("13800004001", "student");
        MockHttpSession hr = login("13800004002", "hr");
        long questionId = createQuestion(student);

        long hrId = currentUserId(hr);
        long answerId = answer(hr, questionId);
        long followupId = followup(student, questionId, hrId);

        mockMvc.perform(post("/api/v1/answers/{id}/withdraw", answerId).session(hr).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("removed"));

        mockMvc.perform(post("/api/v1/questions/{qid}/followups/{fid}/withdraw", questionId, followupId)
                        .session(student).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("removed"));
    }

    @Test
    void onlyAuthorCanWithdrawAndCannotRepeat() throws Exception {
        MockHttpSession student = login("13800004003", "student");
        MockHttpSession hr = login("13800004004", "hr");
        long questionId = createQuestion(student);

        long hrId = currentUserId(hr);
        long answerId = answer(hr, questionId);
        long followupId = followup(student, questionId, hrId);

        mockMvc.perform(post("/api/v1/answers/{id}/withdraw", answerId).session(student).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/answers/{id}/withdraw", answerId).session(hr).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/answers/{id}/withdraw", answerId).session(hr).with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/questions/{qid}/followups/{fid}/withdraw", questionId, followupId)
                        .session(hr).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/questions/{qid}/followups/{fid}/withdraw", questionId, followupId)
                        .session(student).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/questions/{qid}/followups/{fid}/withdraw", questionId, followupId)
                        .session(student).with(csrf()))
                .andExpect(status().isConflict());
    }
}
