package com.careerq.auth;

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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MvcResult register(String phone, String nickname) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", nickname))))
                .andReturn();
    }

    @Test
    void registerThenMe() throws Exception {
        String phone = "13800000001";
        MvcResult result = register(phone, "注册用户");
        org.junit.jupiter.api.Assertions.assertEquals(200, result.getResponse().getStatus());

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session, "注册后应建立会话");

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("student"))
                .andExpect(jsonPath("$.data.nickname").value("注册用户"));
    }

    @Test
    void registerWithoutNicknameUsesDefault() throws Exception {
        String phone = "13800000008";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals("用户0008", body.path("data").path("user").path("nickname").asText());
    }

    @Test
    void loginUnregisteredRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", "13800000002", "code", "123456"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void registerDuplicateRejected() throws Exception {
        String phone = "13800000004";
        org.junit.jupiter.api.Assertions.assertEquals(200, register(phone, "用户A").getResponse().getStatus());
        MvcResult second = register(phone, "用户B");
        org.junit.jupiter.api.Assertions.assertEquals(409, second.getResponse().getStatus());
    }

    @Test
    void loginExistingUser() throws Exception {
        String phone = "13800000005";
        MockHttpSession before = (MockHttpSession) register(phone, "用户C").getRequest().getSession(false);
        assertNotNull(before);
        String previousSessionId = before.getId();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .session(before)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.nickname").value("用户C"))
                .andReturn();
        assertNotEquals(previousSessionId, loginResult.getRequest().getSession(false).getId());
    }

    @Test
    void wrongCodeRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", "13800000006", "code", "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void authWritesRequireCsrf() throws Exception {
        for (String operation : new String[]{"sms-code", "login", "register", "logout"}) {
            mockMvc.perform(post("/api/v1/auth/{operation}", operation))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));
        }
    }

    @Test
    void meRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        String phone = "13800000007";
        MvcResult regResult = register(phone, "注销测试");
        MockHttpSession session = (MockHttpSession) regResult.getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andExpect(status().isOk());

        assertTrue(session.isInvalid(), "登出后会话应失效");

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void registerHrAccountHasHrRole() throws Exception {
        String phone = "13800000610";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", "HR用户", "role", "hr"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("hr"))
                .andReturn();
        assertNotNull(result.getRequest().getSession(false));
    }

    @Test
    void hrCannotCreateQuestion() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", "13800000611", "code", "123456",
                                "nickname", "HR不能提问", "role", "hr"))))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(post("/api/v1/questions").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "HR 不应该能提问",
                                "body", "验证角色限制",
                                "scope", "public"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerInvalidRoleRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", "13800000612", "code", "123456",
                                "nickname", "非法角色", "role", "admin"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}