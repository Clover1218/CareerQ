package com.careerq.question;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.careerq.dao.ReportDao;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * 公开问答边界：仅支持 public，旧工作流路由完全移除。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicOnlyAccessTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReportDao reportDao;

    private void register(String phone, String role) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("phone", phone);
        body.put("code", "123456");
        body.put("nickname", "公开范围测试" + role);
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
    void unsupportedQuestionScopeRejected() throws Exception {
        MockHttpSession student = login("13800001001", "student");
        mockMvc.perform(post("/api/v1/questions").session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "不支持的可见范围",
                                "body", "无效范围内容",
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
    void removedWorkflowRoutesReturnNotFound() throws Exception {
        MockHttpSession hr = login("13800001003", "hr");
        mockMvc.perform(get("/api/v1/hr/private-pool").session(hr))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/hr/questions/1/claim").session(hr).with(csrf()))
                .andExpect(status().isNotFound());
        MockHttpSession student = login("13800001004", "student");
        for (String path : List.of("/api/v1/questions/1/close", "/api/v1/questions/1/publish")) {
            mockMvc.perform(post(path).session(student).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        for (String path : List.of("/api/v1/posts", "/api/v1/feed/highlights")) {
            mockMvc.perform(get(path).session(student))
                    .andExpect(status().isNotFound());
        }
    }
    @Test
    @Transactional
    void retiredStoredRecordsStayOutsidePublicContentAndStats() throws Exception {
        MockHttpSession student = login("13800001005", "student");
        MockHttpSession hr = login("13800001006", "hr");
        long studentId = objectMapper.readTree(mockMvc.perform(get("/api/v1/me").session(student))
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
        long hrId = objectMapper.readTree(mockMvc.perform(get("/api/v1/me").session(hr))
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
        jdbc.update("insert into questions (scope, author_id, title, body, status, answer_count, created_at, updated_at) "
                + "values ('private', ?, 'retired title', 'retired body', 'published', 1, current_timestamp, current_timestamp)", studentId);
        long questionId = jdbc.queryForObject("select id from questions where author_id = ?", Long.class, studentId);
        jdbc.update("insert into answers (question_id, author_id, body, status, created_at, updated_at) "
                + "values (?, ?, 'retired answer', 'published', current_timestamp, current_timestamp)", questionId, hrId);
        long answerId = jdbc.queryForObject("select id from answers where question_id = ?", Long.class, questionId);
        jdbc.update("insert into interactions (user_id, target_type, target_id, type, created_at) "
                + "values (?, 'question', ?, 'favorite', current_timestamp)", studentId, questionId);
        jdbc.update("insert into interactions (user_id, target_type, target_id, type, created_at) "
                + "values (?, 'answer', ?, 'like', current_timestamp)", studentId, answerId);
        jdbc.update("insert into reports (reporter_id, target_type, target_id, reason, detail, status, created_at) "
                + "values (?, 'question', ?, 'test', 'retired report', 'pending', current_timestamp)", studentId, questionId);
        long reportId = jdbc.queryForObject("select id from reports where reporter_id = ?", Long.class, studentId);

        mockMvc.perform(get("/api/v1/questions/{id}", questionId).session(student))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/questions/{id}/content", questionId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/questions/{id}/thread", questionId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/me/questions").session(student))
                .andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(get("/api/v1/me/favorites").session(student))
                .andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(get("/api/v1/me/stats").session(student))
                .andExpect(jsonPath("$.data.questionCount").value(0))
                .andExpect(jsonPath("$.data.receivedAnswerCount").value(0));
        mockMvc.perform(get("/api/v1/me/stats").session(hr))
                .andExpect(jsonPath("$.data.authoredAnswerCount").value(0))
                .andExpect(jsonPath("$.data.likeCount").value(0));
        assertTrue(reportDao.findByIdForUpdate(reportId).isEmpty());
        assertTrue(reportDao.findByStatusOrderByIdAsc("pending").stream().noneMatch(report -> report.getId() == reportId));
    }

}
