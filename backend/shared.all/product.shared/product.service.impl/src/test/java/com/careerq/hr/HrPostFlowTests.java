package com.careerq.hr;

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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HrPostFlowTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    static final AtomicInteger PHONES = new AtomicInteger(1000);
    private MockHttpSession account(String role) throws Exception {
        String phone = "1398000" + PHONES.incrementAndGet();
        var body = Map.of("phone", phone, "code", "123456", "nickname", "发帖测试", "role", role, "company", "某企业");
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private JsonNode read(MockHttpSession session, String path) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1" + path).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
    }
    private JsonNode write(MockHttpSession session, String path, Map<String, Object> body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1" + path).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private Map<String, Object> draftBody(String key) { return Map.of("clientKey", key, "title", "面试观察", "body", "分享岗位准备与项目表达经验。", "tags", List.of("面试准备")); }
    private JsonNode draft(MockHttpSession hr) throws Exception { return write(hr, "/hr/posts/drafts", draftBody(UUID.randomUUID().toString())); }
    private JsonNode save(MockHttpSession hr, long id, long version, String body) throws Exception {
        return json.readTree(mvc.perform(put("/api/v1/hr/posts/{id}/draft", id).session(hr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("version", version, "title", "修改后的分享", "body", body, "tags", List.of("面试准备")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    @Test
    void publicSharePagesOnlyExposePublishedContent() throws Exception {
        var hr = account("hr");
        long firstPublished = draft(hr).path("id").asLong();
        long secondPublished = draft(hr).path("id").asLong();
        for (long id : List.of(firstPublished, secondPublished)) {
            jdbc.update("update hr_posts set status = 'published' where id = ?", id);
        }
        for (String hiddenStatus : List.of("draft", "pending_human", "withdrawn", "rejected", "removed", "deleted")) {
            long id = draft(hr).path("id").asLong();
            jdbc.update("update hr_posts set status = ? where id = ?", hiddenStatus, id);
            mvc.perform(get("/api/v1/hr-shares/{id}", id)).andExpect(status().isNotFound());
        }

        var first = json.readTree(mvc.perform(get("/api/v1/hr-shares").param("limit", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertEquals(1, first.path("items").size());
        assertEquals(secondPublished, first.path("nextCursor").asLong());
        JsonNode summary = first.path("items").get(0);
        assertEquals(secondPublished, summary.path("id").asLong());
        assertEquals("发帖测试", summary.path("authorNickname").asText());
        assertEquals("面试准备", summary.path("tags").get(0).asText());
        assertFalse(summary.has("body"));
        assertFalse(summary.has("version"));
        long hrId = read(hr, "/me").path("id").asLong();
        assertEquals(hrId, summary.path("authorId").asLong());

        var next = json.readTree(mvc.perform(get("/api/v1/hr-shares").param("limit", "1")
                .param("cursor", String.valueOf(secondPublished)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertEquals(firstPublished, next.path("items").get(0).path("id").asLong());
        assertFalse(next.hasNonNull("nextCursor"));
        var detail = json.readTree(mvc.perform(get("/api/v1/hr-shares/{id}", firstPublished))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertEquals("分享岗位准备与项目表达经验。", detail.path("body").asText());
        assertEquals(hrId, detail.path("authorId").asLong());
        assertFalse(detail.has("version"));

        var viewer = account("student");
        var publicProfileShares = json.readTree(mvc.perform(get("/api/v1/users/{id}/hr-shares", hrId).session(viewer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertEquals(2, publicProfileShares.path("items").size());

        write(hr, "/hr/posts/" + firstPublished + "/withdraw", Map.of("version", 1));
        mvc.perform(get("/api/v1/hr-shares/{id}", firstPublished)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/{id}/hr-shares", hrId).session(viewer))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void authorIsolationRoleBoundaryAndIdentityGuard() throws Exception {
        var hr = account("hr"); var other = account("hr"); var student = account("student");
        mvc.perform(get("/api/v1/hr/posts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/hr/posts").session(student)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/hr/posts/drafts").session(student).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(draftBody(UUID.randomUUID().toString())))).andExpect(status().isForbidden());
        var d = draft(hr); long id = d.path("id").asLong();
        mvc.perform(get("/api/v1/hr/posts/{id}", id).session(other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/hr/posts/{id}/submit", id).session(other).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}")).andExpect(status().isNotFound());
        long otherId = read(other, "/me").path("id").asLong();
        var blocked = mvc.perform(post("/api/v1/hr/posts/drafts").session(hr).with(csrf()).header("X-CareerQ-Hr-Id", otherId)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draftBody(UUID.randomUUID().toString()))))
                .andExpect(status().isConflict()).andReturn();
        assertEquals(40902, json.readTree(blocked.getResponse().getContentAsString()).path("code").asInt());
        assertEquals(1, read(hr, "/hr/posts").path("items").size());
        assertEquals(0, read(other, "/hr/posts").path("items").size());
    }
    @Test
    void idempotentDraftSaveVersionConflictSubmitAndWithdraw() throws Exception {
        var hr = account("hr"); String key = UUID.randomUUID().toString();
        var d = write(hr, "/hr/posts/drafts", draftBody(key)); long id = d.path("id").asLong();
        assertEquals(id, write(hr, "/hr/posts/drafts", draftBody(key)).path("id").asLong());
        assertEquals(1, read(hr, "/hr/posts").path("items").size());
        var saved = save(hr, id, 1, "最新服务器正文"); assertEquals(2, saved.path("version").asLong());
        mvc.perform(put("/api/v1/hr/posts/{id}/draft", id).session(hr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("version", 1, "title", "旧页", "body", "不能覆盖", "tags", List.of()))))
                .andExpect(status().isConflict());
        assertEquals("最新服务器正文", read(hr, "/hr/posts/" + id).path("body").asText());
        var submitted = write(hr, "/hr/posts/" + id + "/submit", Map.of("version", 2));
        assertEquals("pending_human", submitted.path("status").asText());
        assertEquals(3, submitted.path("version").asInt());
        mvc.perform(put("/api/v1/hr/posts/{id}/draft", id).session(hr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("version", 3, "title", "待审不能直接改", "body", "x", "tags", List.of()))))
                .andExpect(status().isConflict());
        assertEquals("withdrawn", write(hr, "/hr/posts/" + id + "/withdraw", Map.of("version", 3)).path("status").asText());
        assertEquals("draft", save(hr, id, 4, "撤回后继续编辑").path("status").asText());
        assertEquals("pending_human", write(hr, "/hr/posts/" + id + "/submit", Map.of("version", 5)).path("status").asText());
        assertEquals(4, jdbc.queryForObject("select count(*) from moderation_records where target_type = 'hr_post' and target_id = ?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("select count(*) from questions where title = '修改后的分享'", Integer.class));
    }
    @Test
    void validatesContentAndRetainsBlockedSubmissionForRevision() throws Exception {
        var hr = account("hr"); var d = draft(hr); long id = d.path("id").asLong();
        jdbc.update("insert into moderation_words (word, category, action, updated_at) values ('测试禁止正文', 'spam', 'block', current_timestamp)");
        save(hr, id, 1, "测试禁止正文");
        var rejected = write(hr, "/hr/posts/" + id + "/submit", Map.of("version", 2));
        assertEquals("rejected", rejected.path("status").asText());
        assertEquals("测试禁止正文", rejected.path("body").asText());
        var corrected = save(hr, id, 3, "修改为正常内容"); assertEquals("draft", corrected.path("status").asText());
        mvc.perform(post("/api/v1/hr/posts/drafts").session(hr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("clientKey", UUID.randomUUID().toString(), "title", "a".repeat(201), "body", "b", "tags", List.of()))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/hr/posts/drafts").session(hr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("clientKey", UUID.randomUUID().toString(), "title", "", "body", "", "tags", List.of("不存在标签")))))
                .andExpect(status().isBadRequest());
        var empty = write(hr, "/hr/posts/drafts", Map.of("clientKey", UUID.randomUUID().toString(), "title", "", "body", "", "tags", List.of()));
        mvc.perform(post("/api/v1/hr/posts/{id}/submit", empty.path("id").asLong()).session(hr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}")).andExpect(status().isBadRequest());
    }
    @Test
    void paginationDeleteAndPhaseOneRemainPrivate() throws Exception {
        var hr = account("hr"); var student = account("student");
        for (int i = 0; i < 6; i++) draft(hr);
        var first = read(hr, "/hr/posts?limit=5"); assertEquals(5, first.path("items").size());
        long cursor = first.path("nextCursor").asLong();
        var second = read(hr, "/hr/posts?limit=5&cursor=" + cursor); assertEquals(1, second.path("items").size());
        assertTrue(second.path("nextCursor").isMissingNode() || second.path("nextCursor").isNull());
        long id = second.path("items").get(0).path("id").asLong();
        mvc.perform(delete("/api/v1/hr/posts/{id}/draft", id).session(hr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/hr/posts/{id}", id).session(hr)).andExpect(status().isNotFound());
        assertEquals(5, read(hr, "/hr/posts").path("items").size());
        long pending = first.path("items").get(0).path("id").asLong();
        write(hr, "/hr/posts/" + pending + "/submit", Map.of("version", 1));
        assertEquals(1, read(hr, "/hr/posts?status=pending_human").path("items").size());
        assertEquals(0, read(student, "/feed/questions?q=面试观察").path("items").size());
        mvc.perform(get("/api/v1/hr/posts/{id}", pending)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/hr/posts/{id}", pending).session(student)).andExpect(status().isForbidden());
    }
}
