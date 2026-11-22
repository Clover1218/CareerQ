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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审核台待办队列：固定每页 10 条，同条件下重复请求顺序稳定且相邻页不重叠。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminQueuePagingTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserDao userDao;

    private MockHttpSession login(String phone, String role) throws Exception {
        Map<String, Object> reg = new HashMap<>();
        reg.put("phone", phone);
        reg.put("code", "123456");
        reg.put("nickname", "队列分页" + role);
        reg.put("role", role);
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk());
        return signIn(phone);
    }

    private MockHttpSession signIn(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "code", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession operator(String phone) throws Exception {
        MockHttpSession session = login(phone, "student");
        MvcResult me = mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(me.getResponse().getContentAsString()).path("data").path("id").asLong();
        UserDO user = userDao.findById(id).orElseThrow();
        user.setRole("operator");
        userDao.updateRole(user);
        return signIn(phone);
    }

    private void createQuestion(MockHttpSession student, int index) throws Exception {
        mockMvc.perform(post("/api/v1/questions").session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "队列分页测试问题 " + index,
                                "body", "用于验证审核台待办队列固定十条件分页。",
                                "tags", List.of(),
                                "scope", "public"))))
                .andExpect(status().isOk());
    }

    private JsonNode queue(MockHttpSession operator, int page, int size) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/moderation/queue")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .session(operator))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private Set<String> keys(JsonNode page) {
        Set<String> keys = new HashSet<>();
        for (JsonNode item : page.path("items")) {
            keys.add(item.path("targetType").asText() + ":" + item.path("targetId").asLong());
        }
        return keys;
    }

    @Test
    void queueFetchesTenPerPageAndKeepsOrderStable() throws Exception {
        MockHttpSession student = login("13800007001", "student");
        MockHttpSession operator = operator("13800007002");
        for (int i = 1; i <= 12; i++) {
            createQuestion(student, i);
        }

        JsonNode first = queue(operator, 1, 10);
        JsonNode second = queue(operator, 2, 10);
        assertEquals(10, first.path("items").size(), "第一页应稳定返回 10 条");
        assertTrue(first.path("total").asInt() >= 12, "应包含刚创建的全部待审内容");
        assertEquals(1, first.path("page").asInt());
        assertEquals(10, first.path("size").asInt());
        assertFalse(second.path("items").isEmpty(), "第二页应还有剩余待审内容");

        Set<String> firstKeys = keys(first);
        Set<String> secondKeys = keys(second);
        assertTrue(Collections.disjoint(firstKeys, secondKeys), "相邻页不应出现重复条目");

        assertEquals(firstKeys, keys(queue(operator, 1, 10)), "同条件下重复请求应返回同一页");
        assertEquals(secondKeys, keys(queue(operator, 2, 10)), "翻页顺序不应抖动");
    }
}