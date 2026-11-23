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
 * 提问撤回：仅提问者、未产生回答、未结单时可撤回；撤回后不进公开列表。
 */
@SpringBootTest
@AutoConfigureMockMvc
class WithdrawFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession login(String phone, String role) throws Exception {
        Map<String, Object> reg = new java.util.HashMap<>();
        reg.put("phone", phone);
        reg.put("code", "123456");
        reg.put("nickname", "撤回测试" + role);
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

    private long createQuestion(MockHttpSession session, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "body", "撤回测试问题正文",
                                "tags", List.of("实习"),
                                "scope", "public"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    @Test
    void authorCanWithdrawAndPublicFeedHidesIt() throws Exception {
        MockHttpSession student = login("13800003001", "student");
        long id = createQuestion(student, "待撤回的问题A");

        mockMvc.perform(post("/api/v1/questions/{id}/withdraw", id).session(student).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayStatus").value("WITHDRAWN"));

        MvcResult feed = mockMvc.perform(get("/api/v1/questions").param("tag", "实习"))
                .andExpect(status().isOk())
                .andReturn();
        String body = feed.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("待撤回的问题A"),
                "撤回的问题不应出现在公开列表");

        // 作者自己的列表仍能看到，状态为已撤回
        mockMvc.perform(get("/api/v1/questions/mine").session(student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + id + ")].displayStatus").value("WITHDRAWN"));
    }

    @Test
    void onlyAuthorCanWithdraw() throws Exception {
        MockHttpSession author = login("13800003002", "student");
        MockHttpSession other = login("13800003003", "student");
        long id = createQuestion(author, "别人的问题B");

        mockMvc.perform(post("/api/v1/questions/{id}/withdraw", id).session(other).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotWithdrawTwiceOrAfterAnswer() throws Exception {
        MockHttpSession student = login("13800003004", "student");
        long answered = createQuestion(student, "已回答的问题C");
        MockHttpSession hr = login("13800003005", "hr");
        mockMvc.perform(post("/api/v1/questions/{id}/answers", answered).session(hr).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "先梳理清楚自己的优先级。"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/questions/{id}/withdraw", answered).session(student).with(csrf()))
                .andExpect(status().isConflict());

        long plain = createQuestion(student, "重复撤回想");
        mockMvc.perform(post("/api/v1/questions/{id}/withdraw", plain).session(student).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/questions/{id}/withdraw", plain).session(student).with(csrf()))
                .andExpect(status().isConflict());
    }
}
