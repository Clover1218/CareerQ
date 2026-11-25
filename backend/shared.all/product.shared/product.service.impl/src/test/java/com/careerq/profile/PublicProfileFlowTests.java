package com.careerq.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicProfileFlowTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private MockHttpSession register(String phone, String role) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "phone", phone, "code", "123456", "role", role,
                        "company", "演示企业", "school", "测试学校", "major", "计算机", "grade", "大三"))))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private JsonNode read(MockHttpSession session, String path) throws Exception {
        var request = get("/api/v1" + path);
        if (session != null) request.session(session);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString()).path("data");
    }

    private JsonNode create(MockHttpSession session, String path, Map<String, Object> body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1" + path).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    private void setEducation(MockHttpSession student, boolean visible) throws Exception {
        mvc.perform(patch("/api/v1/me/profile").session(student).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "educationPublic", visible, "publicBio", "求职准备中"))))
                .andExpect(status().isOk());
    }

    @Test
    void educationVisibilityAndPublishedContentAreRespected() throws Exception {
        var student = register("13976001001", "student");
        var hr = register("13976001002", "hr");
        long studentId = read(student, "/me").path("id").asLong();
        long hrId = read(hr, "/me").path("id").asLong();
        String studentPath = "/users/" + studentId;
        String hrPath = "/users/" + hrId;
        for (String path : new String[]{studentPath + "/profile", studentPath + "/questions",
                hrPath + "/answers", hrPath + "/hr-shares"}) {
            mvc.perform(get("/api/v1" + path)).andExpect(status().isUnauthorized());
        }
        var publicStudent = read(hr, studentPath + "/profile");
        assertFalse(publicStudent.hasNonNull("school"));
        assertFalse(publicStudent.hasNonNull("major"));
        assertFalse(publicStudent.hasNonNull("grade"));
        assertFalse(publicStudent.has("experiences"));
        setEducation(student, true);
        assertEquals("测试学校", read(hr, studentPath + "/profile").path("school").asText());
        assertEquals("求职准备中", read(hr, studentPath + "/profile").path("bio").asText());
        setEducation(student, false);
        assertFalse(read(hr, studentPath + "/profile").hasNonNull("school"));
        assertEquals("测试学校", read(student, "/me/profile").path("school").asText());

        long visible = create(student, "/questions", Map.of("title", "公开提问", "body", "如何准备项目面试？")).path("id").asLong();
        long hidden = create(student, "/questions", Map.of("title", "隐藏提问", "body", "如何提高面试表达能力？")).path("id").asLong();
        long answer = create(hr, "/questions/" + visible + "/answers", Map.of("body", "先整理项目经历。")).path("id").asLong();
        long hiddenAnswer = create(hr, "/questions/" + visible + "/answers", Map.of("body", "这是一条待复审的内容。")).path("id").asLong();
        create(hr, "/questions/" + hidden + "/answers", Map.of("body", "问题不可见后的回答。"));
        jdbc.update("update questions set visibility = 'HIDDEN' where id = ?", hidden);
        jdbc.update("update answers set visibility = 'HIDDEN' where id = ?", hiddenAnswer);

        var questions = read(hr, studentPath + "/questions").path("items");
        assertEquals(1, questions.size());
        assertEquals(visible, questions.get(0).path("id").asLong());
        var answers = read(student, hrPath + "/answers").path("items");
        assertEquals(1, answers.size());
        assertEquals(answer, answers.get(0).path("id").asLong());
        assertEquals(1, read(student, hrPath + "/profile").path("stats").path("rootAnswerCount").asInt());
        assertEquals("演示企业", read(student, hrPath + "/profile").path("companyDisplay").asText());
        create(hr, "/answers/" + answer + "/withdraw", Map.of());
        assertEquals(0, read(student, hrPath + "/answers").path("items").size());
    }
}
