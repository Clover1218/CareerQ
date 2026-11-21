package com.careerq.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnswerThreadsFlowTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private MockHttpSession register(String phone, String role) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("phone", phone, "code", "123456", "role", role,
                        "nickname", "线程测试", "company", "某行业企业"))))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private JsonNode write(MockHttpSession session, String path, Map<String, Object> body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1" + path).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    private JsonNode read(MockHttpSession session, String path) throws Exception {
        var request = get("/api/v1" + path);
        if (session != null) request.session(session);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString()).path("data");
    }

    @Test
    void independentAnswersKeepTargetsAndDescendantsWithoutLeakingRemovedBodies() throws Exception {
        var student = register("13800009711", "student");
        var hr = register("13800009712", "hr");
        var other = register("13800009713", "hr");
        long hrId = read(hr, "/me").path("id").asLong();
        long questionId = write(student, "/questions", Map.of("title", "独立回答关系验证", "body", "如何准备实习面试？", "scope", "public"))
                .path("id").asLong();
        String base = "/questions/" + questionId;
        long firstId = write(hr, base + "/answers", Map.of("body", "第一条回答", "lengthType", "detailed")).path("id").asLong();
        long secondId = write(hr, base + "/answers", Map.of("body", "第二条回答撤销后不可泄漏", "lengthType", "detailed")).path("id").asLong();
        long followupId = write(student, base + "/followups", Map.of("hrId", hrId, "rootAnswerId", secondId, "body", "针对第二条回答的追问"))
                .path("id").asLong();
        mvc.perform(post("/api/v1" + base + "/followups").session(student).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("hrId", hrId, "body", "缺少具体回答"))))
                .andExpect(status().isBadRequest());
        assertEquals(2, read(null, base).path("answers").size());
        long replyId = write(hr, base + "/followups/" + followupId + "/answers", Map.of("body", "该追问的公开回复"))
                .path("id").asLong();
        mvc.perform(post("/api/v1/answers/{id}/withdraw", replyId).session(other).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1" + base + "/followups/" + followupId + "/answers")
                        .session(other).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"不能跨 HR 回复\"}"))
                .andExpect(status().isNotFound());
        write(hr, "/answers/" + secondId + "/withdraw", Map.of());
        JsonNode detail = read(null, base);
        JsonNode placeholder = null;
        for (JsonNode answer : detail.path("answers")) if (answer.path("id").asLong() == secondId) placeholder = answer;
        assertNotNull(placeholder);
        assertEquals("removed", placeholder.path("status").asText());
        assertEquals("", placeholder.path("body").asText());
        assertFalse(detail.toString().contains("第二条回答撤销后不可泄漏"));
        write(student, base + "/followups/" + followupId + "/withdraw", Map.of());
        JsonNode thread = read(null, base + "/thread");
        boolean foundFirst = false, foundReply = false, foundFollowup = false;
        for (JsonNode item : thread.path("items")) {
            if (item.path("kind").asText().equals("hr_answer") && item.path("id").asLong() == firstId) foundFirst = true;
            if (item.path("kind").asText().equals("followup_answer") && item.path("id").asLong() == replyId) foundReply = true;
            if (item.path("kind").asText().equals("followup") && item.path("id").asLong() == followupId) {
                foundFollowup = true;
                assertEquals(secondId, item.path("rootAnswerId").asLong());
                assertEquals("", item.path("body").asText());
            }
        }
        assertTrue(foundFirst && foundReply && foundFollowup);
        assertFalse(thread.toString().contains("第二条回答撤销后不可泄漏"));
        write(hr, "/answers/" + replyId + "/withdraw", Map.of());
        mvc.perform(post("/api/v1/answers/{id}/withdraw", replyId).session(hr).with(csrf()))
                .andExpect(status().isConflict());
    }
}
