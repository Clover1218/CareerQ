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
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HrPersonalFlowTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private MockHttpSession account(String phone, String role) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("phone", phone, "code", "123456", "nickname", "个人中心测试", "role", role, "company", "某行业企业"))))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
    private JsonNode read(MockHttpSession session, String path) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1" + path).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
    }
    private JsonNode write(MockHttpSession session, String path, Map<String, Object> body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1" + path).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
    }
    @Test
    void ownProfileAndEveryIndependentAnswerHaveCorrectFiltersPaginationAndStatistics() throws Exception {
        var hr = account("13975002001", "hr");
        var other = account("13975002002", "hr");
        var student = account("13975002003", "student");
        mvc.perform(patch("/api/v1/hr/profile").session(hr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nickname", "匿名答主", "industry", "互联网", "position", "校园招聘",
                                "bio", "分享面试经验", "specialties", java.util.List.of("面试", "面试", "校招"), "verifyStatus", "approved", "role", "admin"))))
                .andExpect(status().isOk());
        var profile = read(hr, "/hr/profile");
        assertEquals("匿名答主", read(hr, "/me").path("nickname").asText());
        assertEquals("hr", read(hr, "/me").path("role").asText());
        assertEquals("pending", profile.path("verifyStatus").asText());
        assertEquals(2, profile.path("specialties").size());
        assertFalse(profile.has("verifyMaterialRef"));
        assertNotEquals("匿名答主", read(other, "/hr/profile").path("nickname").asText());
        long qid = write(student, "/questions", Map.of("title", "同题多条回答", "body", "怎样准备面试？")).path("id").asLong();
        String base = "/questions/" + qid;
        long root = 0;
        for (int i = 0; i < 6; i++) {
            long id = write(hr, base + "/answers", Map.of("body", "独立回答 " + i)).path("id").asLong();
            if (i == 0) root = id;
            if (i == 1) write(hr, "/answers/" + id + "/withdraw", Map.of());
        }
        write(other, base + "/answers", Map.of("body", "其他答主的内容"));
        write(hr, base + "/answers", Map.of("body", "可以加微信咨询"));
        long hrId = profile.path("id").asLong();
        long f = write(student, base + "/followups", Map.of("hrId", hrId, "rootAnswerId", root, "body", "请展开说说？")).path("id").asLong();
        write(hr, base + "/followups/" + f + "/answers", Map.of("body", "追问回复正文"));
        for (String type : new String[]{"like", "useful"}) write(student, "/interactions", Map.of("targetType", "answer", "targetId", root, "type", type));
        var stats = read(hr, "/hr/stats");
        assertEquals(6, stats.path("rootAnswerCount").asInt());
        assertEquals(1, stats.path("followupReplyCount").asInt());
        assertEquals(1, stats.path("likeCount").asInt());
        assertEquals(1, stats.path("usefulCount").asInt());
        JsonNode first = read(hr, "/hr/answers?limit=5");
        assertEquals(5, first.path("items").size());
        long cursor = first.path("nextCursor").asLong();
        assertTrue(cursor > 0);
        JsonNode second = read(hr, "/hr/answers?limit=5&cursor=" + cursor);
        assertEquals(3, second.path("items").size());
        assertTrue(second.path("nextCursor").isMissingNode() || second.path("nextCursor").isNull());
        var ids = new HashSet<Long>();
        for (JsonNode page : new JsonNode[]{first, second}) for (JsonNode item : page.path("items")) assertTrue(ids.add(item.path("id").asLong()));
        var removed = read(hr, "/hr/answers?status=withdrawn").path("items");
        assertEquals(1, removed.size());
        assertEquals("", removed.get(0).path("body").asText());
        assertEquals("author", removed.get(0).path("removalSource").asText());
        assertEquals(0, read(hr, "/hr/answers?status=reviewing").path("items").size());
        assertEquals(6, read(hr, "/hr/answers?status=published&kind=root&limit=50").path("items").size());
        assertEquals(1, read(hr, "/hr/answers?kind=reply").path("items").size());
        assertEquals(1, read(other, "/hr/answers?hrId=" + hrId).path("items").size());
    }

    @Test
    void nonHrAndInvalidInputsCannotAccessOrChangeHrData() throws Exception {
        var hr = account("13975002004", "hr");
        var student = account("13975002005", "student");
        for (String path : new String[]{"/profile", "/stats", "/answers"}) {
            mvc.perform(get("/api/v1/hr" + path).session(student)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/hr" + path)).andExpect(status().isUnauthorized());
        }
        for (String query : new String[]{"kind=invalid", "status=invalid", "limit=0", "limit=51", "cursor=-1"}) {
            mvc.perform(get("/api/v1/hr/answers?" + query).session(hr)).andExpect(status().isBadRequest());
        }
        String original = read(hr, "/hr/profile").path("nickname").asText();
        mvc.perform(patch("/api/v1/hr/profile").session(hr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"  \",\"industry\":\"不应保存\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(original, read(hr, "/hr/profile").path("nickname").asText());
        mvc.perform(patch("/api/v1/hr/profile").session(student).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"不能修改\"}"))
                .andExpect(status().isForbidden());
    }
}
