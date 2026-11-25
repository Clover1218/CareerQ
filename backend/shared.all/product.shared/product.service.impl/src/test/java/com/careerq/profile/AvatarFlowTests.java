package com.careerq.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 头像上传安全约束：只接受图片、限制大小、校验魔数、站内读取。
 */
@SpringBootTest(properties = "careerq.avatar.dir=target/test-avatars")
@AutoConfigureMockMvc
class AvatarFlowTests {

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession register(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", "头像用户"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void uploadPngAndReadItBack() throws Exception {
        MockHttpSession session = register("13800002001");
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES);

        MvcResult upload = mockMvc.perform(multipart("/api/v1/me/avatar").file(file).with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").isNotEmpty())
                .andReturn();
        String avatarUrl = objectMapper.readTree(upload.getResponse().getContentAsString())
                .path("data").path("avatarUrl").asText();
        org.junit.jupiter.api.Assertions.assertTrue(avatarUrl.startsWith("/api/v1/avatars/"),
                "应返回站内相对路径: " + avatarUrl);

        mockMvc.perform(get(avatarUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png"));

        MvcResult question = mockMvc.perform(post("/api/v1/questions").session(session).with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "发布后的头像展示", "body", "如何准备项目面试？"))))
                .andExpect(status().isOk())
                .andReturn();
        long questionId = objectMapper.readTree(question.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(get("/api/v1/questions/{id}", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.author.avatarUrl").value(avatarUrl));
    }

    @Test
    void nonImageRejected() throws Exception {
        MockHttpSession session = register("13800002002");
        MockMultipartFile file = new MockMultipartFile("file", "evil.png", "image/png",
                "not an image".getBytes());
        mockMvc.perform(multipart("/api/v1/me/avatar").file(file).with(csrf()).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void oversizedRejected() throws Exception {
        MockHttpSession session = register("13800002003");
        byte[] big = new byte[3 * 1024 * 1024];
        System.arraycopy(PNG_BYTES, 0, big, 0, PNG_BYTES.length);
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", big);
        mockMvc.perform(multipart("/api/v1/me/avatar").file(file).with(csrf()).session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsafeFilenameRejected() throws Exception {
        mockMvc.perform(get("/api/v1/avatars/evil.txt"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/avatars/1-../../application.yml"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void uploadRequiresLogin() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES);
        mockMvc.perform(multipart("/api/v1/me/avatar").file(file).with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
