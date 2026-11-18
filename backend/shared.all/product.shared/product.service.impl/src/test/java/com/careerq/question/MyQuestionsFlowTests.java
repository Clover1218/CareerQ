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
 * 学生工作区：我的问题（GET /api/v1/questions/mine）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class MyQuestionsFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession register(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", "我的问题" + phone.substring(7)))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void createQuestion(MockHttpSession session, String title, String scope) throws Exception {
        mockMvc.perform(post("/api/v1/questions").with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "body", "问题描述内容",
                                "tags", List.of(),
                                "scope", scope))))
                .andExpect(status().isOk());
    }

    @Test
    void mineReturnsOwnPublicQuestionsAndRejectsPrivateScope() throws Exception {
        MockHttpSession session = register("13800000801");
        createQuestion(session, "公开提问测试", "public");
        createQuestion(session, "公开提问测试二", "public");

        mockMvc.perform(get("/api/v1/questions/mine").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));

        mockMvc.perform(get("/api/v1/questions/mine").session(session).param("scope", "private"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mineOnlyReturnsOwn() throws Exception {
        MockHttpSession first = register("13800000802");
        MockHttpSession second = register("13800000803");
        createQuestion(first, "第一个人的问题", "public");

        mockMvc.perform(get("/api/v1/questions/mine").session(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void mineRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/questions/mine"))
                .andExpect(status().isUnauthorized());
    }
}
