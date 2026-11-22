package com.careerq.moderation;

import com.careerq.dao.UserDao;
import com.careerq.dao.UserPermissionDao;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 撤销、审核次数与仲裁：A 审 A 撤 → B 审 B 撤 → 仲裁终局。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ModerationUndoArbitrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserDao userDao;

    @Autowired
    private UserPermissionDao userPermissionDao;

    private MockHttpSession register(String phone, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone, "code", "123456", "nickname", nickname))))
                .andReturn();
        if (result.getResponse().getStatus() == 200) {
            return (MockHttpSession) result.getRequest().getSession(false);
        }
        if (result.getResponse().getStatus() != 409) {
            throw new IllegalStateException("注册失败，status=" + result.getResponse().getStatus());
        }
        return login(phone);
    }

    private MockHttpSession login(String phone) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private MockHttpSession operator(String phone, String nickname, boolean arbitrate) throws Exception {
        MockHttpSession session = register(phone, nickname);
        MvcResult me = mockMvc.perform(get("/api/v1/me").session(session)).andReturn();
        long id = objectMapper.readTree(me.getResponse().getContentAsString()).path("data").path("id").asLong();
        UserDO user = userDao.findById(id).orElseThrow();
        user.setRole("operator");
        userDao.updateRole(user);
        if (arbitrate) {
            userPermissionDao.insert(id, "arbitrate");
        }
        return login(phone);
    }

    private MockHttpSession admin(String phone, String nickname) throws Exception {
        MockHttpSession session = register(phone, nickname);
        MvcResult me = mockMvc.perform(get("/api/v1/me").session(session)).andReturn();
        long id = objectMapper.readTree(me.getResponse().getContentAsString()).path("data").path("id").asLong();
        UserDO user = userDao.findById(id).orElseThrow();
        user.setRole("admin");
        userDao.updateRole(user);
        return login(phone);
    }

    private long createQuestion(MockHttpSession student, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/questions").with(csrf())
                        .session(student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "body", "这是一段正常的公开问题描述，用于验证撤销与仲裁状态流转。",
                                "tags", List.of(),
                                "scope", "public"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void decide(MockHttpSession operator, long id, String decision, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/admin/moderation/question/" + id + "/decide")
                        .session(operator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", decision, "note", "撤销仲裁测试"))))
                .andExpect(status().is(expectedStatus));
    }

    private void undo(MockHttpSession operator, long id, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/admin/moderation/question/" + id + "/undo")
                        .session(operator).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "撤销原因"))))
                .andExpect(status().is(expectedStatus));
    }

    private JsonNode detail(MockHttpSession operator, long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/moderation/question/" + id).session(operator))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode queue(MockHttpSession operator) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/moderation/queue")
                        .param("type", "question").param("size", "50").session(operator))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("items");
    }

    private JsonNode reviewed(MockHttpSession operator) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/moderation/reviewed")
                        .param("type", "question").param("size", "50").session(operator))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("items");
    }

    private JsonNode find(JsonNode items, long questionId) {
        for (JsonNode item : items) {
            if ("question".equals(item.path("targetType").asText()) && item.path("targetId").asLong() == questionId) {
                return item;
            }
        }
        return null;
    }

    private JsonNode recordOfStage(JsonNode detail, String stage) {
        for (JsonNode record : detail.path("records")) {
            if (stage.equals(record.path("stage").asText())) {
                return record;
            }
        }
        return null;
    }

    @Test
    void undoConsumesAttemptAndLocksOperatorOut() throws Exception {
        MockHttpSession student = register("13800000931", "撤销学生");
        long questionId = createQuestion(student, "撤销后回到待审池");
        MockHttpSession operatorA = operator("13800000932", "审核A", false);
        MockHttpSession operatorB = operator("13800000933", "审核B", false);

        decide(operatorA, questionId, "approve", 200);
        JsonNode beforeUndo = detail(operatorA, questionId);
        assertEquals("PUBLISHED", beforeUndo.path("item").path("displayStatus").asText());
        assertEquals(1, beforeUndo.path("item").path("reviewAttempts").asInt());

        JsonNode mine = find(reviewed(operatorA), questionId);
        assertNotNull(mine, "我审核的应包含刚裁定的内容");
        assertTrue(mine.path("canUndo").asBoolean(), "本人最新裁定应允许撤销");

        undo(operatorA, questionId, 200);
        JsonNode afterUndo = detail(operatorA, questionId);
        assertEquals("REVIEWING", afterUndo.path("item").path("displayStatus").asText());
        assertEquals(1, afterUndo.path("item").path("reviewAttempts").asInt());
        assertNotNull(recordOfStage(afterUndo, "human"), "撤销应留 human 记录");
        assertTrue(find(reviewed(operatorA), questionId) == null, "撤销后不应留在已审列表");

        decide(operatorA, questionId, "approve", 403);
        undo(operatorA, questionId, 409);

        // B 继续裁定，同一条内容允许换人审核
        decide(operatorB, questionId, "reject", 200);
        assertEquals(2, detail(operatorB, questionId).path("item").path("reviewAttempts").asInt());
    }

    @Test
    void secondUndoEntersArbitrationAndArbitrationIsTerminal() throws Exception {
        MockHttpSession student = register("13800000934", "仲裁学生");
        long questionId = createQuestion(student, "两次撤销后进入仲裁");
        MockHttpSession operatorA = operator("13800000935", "仲裁审核A", false);
        MockHttpSession operatorB = operator("13800000936", "仲裁审核B", false);
        MockHttpSession operatorC = operator("13800000937", "仲裁审核C", false);
        MockHttpSession arbiter = operator("13800000938", "仲裁员", true);
        MockHttpSession otherArbiter = operator("13800000939", "旁听仲裁员", true);
        MockHttpSession admin = admin("13800000940", "仲裁管理员");

        decide(operatorA, questionId, "approve", 200);
        undo(operatorA, questionId, 200);
        decide(operatorB, questionId, "approve", 200);
        undo(operatorB, questionId, 200);

        JsonNode arbitrating = detail(operatorA, questionId);
        assertEquals("ARBITRATING", arbitrating.path("item").path("displayStatus").asText());
        assertEquals(2, arbitrating.path("item").path("reviewAttempts").asInt());
        assertTrue(arbitrating.path("item").path("arbitrationRequired").asBoolean());

        // 非仲裁员看不到待仲裁单子，也不能裁定
        assertNull(find(queue(operatorC), questionId), "普通运营队列不应返回待仲裁内容");
        decide(operatorC, questionId, "approve", 403);

        JsonNode arbiterItem = find(queue(arbiter), questionId);
        assertNotNull(arbiterItem, "仲裁员队列应返回待仲裁内容");
        assertTrue(arbiterItem.path("arbitrationRequired").asBoolean());

        decide(arbiter, questionId, "approve", 200);
        JsonNode finalDetail = detail(arbiter, questionId);
        assertEquals("PUBLISHED", finalDetail.path("item").path("displayStatus").asText());
        assertEquals(3, finalDetail.path("item").path("reviewAttempts").asInt());
        JsonNode arbitrationRecord = recordOfStage(finalDetail, "ARBITRATE");
        assertNotNull(arbitrationRecord, "仲裁裁定应写 ARBITRATE 记录");
        assertEquals("approve", arbitrationRecord.path("decision").asText());

        JsonNode arbiterReviewed = find(reviewed(arbiter), questionId);
        assertNotNull(arbiterReviewed, "仲裁裁定应出现在我审核的");
        assertFalse(arbiterReviewed.path("canUndo").asBoolean(), "仲裁裁定不可撤销");

        undo(arbiter, questionId, 403);
        undo(operatorB, questionId, 403);
        undo(otherArbiter, questionId, 403);
        undo(admin, questionId, 403);
        decide(operatorC, questionId, "reject", 409);
        decide(admin, questionId, "reject", 409);
        decide(otherArbiter, questionId, "reject", 409);
    }

    @Test
    void arbiterDecisionIsAlwaysTerminal() throws Exception {
        MockHttpSession student = register("13800000945", "首审学生");
        long questionId = createQuestion(student, "仲裁员首审即终局");
        MockHttpSession operator = operator("13800000946", "普通审核员", false);
        MockHttpSession arbiter = operator("13800000947", "首审仲裁员", true);
        MockHttpSession admin = admin("13800000948", "首审管理员");

        // 审核次数为 0 时，仲裁权限者的裁定也必须是终局
        decide(arbiter, questionId, "reject", 200);
        JsonNode detail = detail(arbiter, questionId);
        assertEquals("REJECTED", detail.path("item").path("displayStatus").asText());
        assertEquals(1, detail.path("item").path("reviewAttempts").asInt());
        assertNotNull(recordOfStage(detail, "ARBITRATE"), "仲裁权限者的首审也应写 ARBITRATE 记录");
        JsonNode mine = find(reviewed(arbiter), questionId);
        assertNotNull(mine);
        assertFalse(mine.path("canUndo").asBoolean(), "仲裁裁定不可撤销");

        undo(arbiter, questionId, 403);
        undo(operator, questionId, 403);
        undo(admin, questionId, 403);
        decide(operator, questionId, "approve", 409);
        decide(admin, questionId, "approve", 409);
    }
}