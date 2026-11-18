package com.careerq.privateflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

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

    private MockHttpSession login(String phone) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", "测试用户"))));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void privateQuestionsAreRejectedAndPublicQuestionsRemainAvailable() throws Exception {
        MockHttpSession student = login("13800000201");

        mockMvc.perform(post("/api/v1/questions").session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "私密问题不再受支持",
                                "body", "该功能暂时下线",
                                "scope", "private"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/questions").session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "公开问题仍可提交",
                                "body", "请问如何准备面试？",
                                "scope", "public"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("public"));

        mockMvc.perform(get("/api/v1/questions/mine?scope=private").session(student))
                .andExpect(status().isBadRequest());
    }

    @Test
    void privateWorkflowEndpointsAreRemoved() throws Exception {
        MockHttpSession hr = login("13800000202");
        MockHttpSession student = login("13800000203");

        mockMvc.perform(get("/api/v1/hr/private-pool").session(hr))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/hr/questions/{id}/claim", 1).session(hr).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/questions/{id}/close", 1).session(student).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/questions/{id}/publish", 1).session(student).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
