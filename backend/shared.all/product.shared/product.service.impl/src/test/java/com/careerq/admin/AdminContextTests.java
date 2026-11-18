package com.careerq.admin;

import com.careerq.dao.UserDao;
import com.careerq.dataobject.UserDO;
import com.fasterxml.jackson.databind.JsonNode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 运营审核上下文：队列要带问题/作者信息，举报列表要带被举报内容摘要。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserDao userDao;

    private MockHttpSession register(String phone, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", nickname))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession loginAs(String phone, String role) throws Exception {
        MockHttpSession session = register(phone, "审核测试" + role);
        MvcResult me = mockMvc.perform(get("/api/v1/me").session(session)).andReturn();
        long id = objectMapper.readTree(me.getResponse().getContentAsString()).path("data").path("id").asLong();
        UserDO user = userDao.findById(id).orElseThrow();
        user.setRole(role);
        userDao.updateRole(user);
        // 角色变更后重新登录，让会话里的 principal 带上新角色
        MvcResult relogin = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) relogin.getRequest().getSession(false);
    }

    private long createQuestion(MockHttpSession session, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions").with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "body", "问题描述内容，用于审核上下文测试，加微信详聊",
                                "tags", List.of(),
                                "scope", "public"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    @Test
    void queueCarriesQuestionAndAuthorContext() throws Exception {
        MockHttpSession student = register("13800000701", "队列学生");
        long questionId = createQuestion(student, "私密待审问题上下文");

        MockHttpSession operator = loginAs("13800000702", "operator");
        MvcResult queueResult = mockMvc.perform(get("/api/v1/admin/moderation/queue").session(operator))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(queueResult.getResponse().getContentAsString()).path("data");

        JsonNode matched = null;
        for (JsonNode item : items) {
            if ("question".equals(item.path("targetType").asText())
                    && item.path("targetId").asLong() == questionId) {
                matched = item;
                break;
            }
        }
        assertNotNull(matched, "待审队列应包含该私密问题");
        assertEquals(questionId, matched.path("questionId").asLong());
        assertEquals("私密待审问题上下文", matched.path("questionTitle").asText());
        assertEquals("队列学生", matched.path("authorNickname").asText());
        assertFalse(matched.path("body").asText().isBlank(), "队列应包含正文供审核");
    }

    @Test
    void reportListCarriesTargetContext() throws Exception {
        MockHttpSession student = register("13800000703", "举报学生");
        long questionId = createQuestion(student, "被举报的问题标题");

        mockMvc.perform(post("/api/v1/reports").with(csrf()).session(student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "targetType", "question", "targetId", questionId,
                                "reason", "spam", "detail", "举报测试"))))
                .andExpect(status().isOk());

        MockHttpSession operator = loginAs("13800000704", "operator");
        MvcResult reportsResult = mockMvc.perform(get("/api/v1/admin/reports?status=pending").session(operator))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(reportsResult.getResponse().getContentAsString()).path("data");
        assertTrue(items.size() > 0, "应有待处理举报");

        JsonNode matched = null;
        for (JsonNode item : items) {
            if (item.path("targetId").asLong() == questionId) {
                matched = item;
                break;
            }
        }
        assertNotNull(matched, "举报列表应包含该目标的举报");
        assertEquals("被举报的问题标题", matched.path("targetTitle").asText());
        assertFalse(matched.path("targetExcerpt").asText().isBlank(), "举报列表应带内容摘要");

        // 清理：处理掉这条举报，避免影响其它测试的“第一条举报”假设
        long reportId = matched.path("id").asLong();
        mockMvc.perform(post("/api/v1/admin/reports/" + reportId + "/handle").with(csrf()).session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "dismiss", "note", "上下文测试清理"))))
                .andExpect(status().isOk());
    }

    @Test
    void studentCannotSeeAdminContext() throws Exception {
        MockHttpSession student = register("13800000705", "普通学生");
        mockMvc.perform(get("/api/v1/admin/moderation/queue").session(student))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanReviewButCannotManageModerationWords() throws Exception {
        MockHttpSession operator = loginAs("13800000706", "operator");
        mockMvc.perform(get("/api/v1/admin/moderation/queue").session(operator))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/moderation/words").session(operator))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/moderation/words").session(operator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "word", "不应由运营配置", "category", "test", "action", "block"))))
                .andExpect(status().isForbidden());
    }
}
