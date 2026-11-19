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

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 团队已明确不做私密问答：创建拒绝、HR 私密池/认领接口禁用。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PrivateFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private void register(String phone, String role) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("phone", phone);
        body.put("code", "123456");
        body.put("nickname", "私密下线测试" + role);
        body.put("role", role);
        if ("hr".equals(role)) {
            body.put("company", "测试公司");
        }
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private MockHttpSession login(String phone, String role) throws Exception {
        register(phone, role);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void privateQuestionCreationRejected() throws Exception {
        MockHttpSession student = login("13800001001", "student");
        mockMvc.perform(post("/api/v1/questions").session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "私密提问已下线",
                                "body", "私密内容",
                                "tags", List.of("实习"),
                                "scope", "private"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void publicQuestionStillWorks() throws Exception {
        MockHttpSession student = login("13800001002", "student");
        mockMvc.perform(post("/api/v1/questions").session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "公开提问仍然可用",
                                "body", "公开问题正文",
                                "tags", List.of("实习"),
                                "scope", "public"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("public"))
                .andExpect(jsonPath("$.data.status").value("published"));
    }

    @Test
    void hrPrivatePoolAndClaimDisabled() throws Exception {
        MockHttpSession hr = login("13800001003", "hr");
        mockMvc.perform(get("/api/v1/hr/private-pool").session(hr))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(post("/api/v1/hr/questions/1/claim").session(hr).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}