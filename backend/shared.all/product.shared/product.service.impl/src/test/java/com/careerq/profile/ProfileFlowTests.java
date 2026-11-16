package com.careerq.profile;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

private MockHttpSession registerHr(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", "HR资料用户",
                                "role", "hr", "company", "某互联网公司", "position", "HRBP"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession register(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", "资料用户"))))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void optionalProfileCanBeFilledAndRead() throws Exception {
        MockHttpSession session = register("13800000401");

        mockMvc.perform(get("/api/v1/me/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("资料用户"))
                .andExpect(jsonPath("$.data.experiences.length()").value(0));

        Map<String, Object> body = Map.of(
                "school", "某大学",
                "major", "计算机",
                "grade", "大三",
                "degree", "本科",
                "graduationYear", "2027",
                "experiences", List.of(
                        Map.of("type", "internship", "title", "某互联网公司", "role", "后端实习生",
                                "startDate", "2025-06", "endDate", "2025-09", "description", "参与订单服务开发"),
                        Map.of("type", "project", "title", "校园二手交易平台", "role", "负责人",
                                "startDate", "2025-03", "endDate", "2025-06", "description", "负责整体架构与后端")));

        mockMvc.perform(put("/api/v1/me/profile")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.school").value("某大学"))
                .andExpect(jsonPath("$.data.degree").value("本科"))
                .andExpect(jsonPath("$.data.experiences.length()").value(2))
                .andExpect(jsonPath("$.data.experiences[0].type").value("internship"));

        mockMvc.perform(get("/api/v1/me/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graduationYear").value("2027"))
                .andExpect(jsonPath("$.data.experiences[1].title").value("校园二手交易平台"));
    }

    @Test
    void anonymousCannotReadProfile() throws Exception {
        mockMvc.perform(get("/api/v1/me/profile"))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void avatarCanBeUpdated() throws Exception {
        MockHttpSession session = register("13800000402");
        mockMvc.perform(put("/api/v1/me/avatar").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "avatarUrl", "https://example.com/a.png"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/a.png"));

        mockMvc.perform(get("/api/v1/me/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/a.png"));
    }


}