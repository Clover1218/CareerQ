package com.careerq.service.impl;

import com.careerq.common.*;
import com.careerq.dao.*;
import com.careerq.dataobject.*;
import com.careerq.hr.HrPostDtos.*;
import com.careerq.security.AuthUser;
import com.careerq.service.HrPostService;
import com.careerq.service.ModerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class HrPostServiceImpl implements HrPostService {
    private final HrPostDao posts;
    private final UserDao users;
    private final TagDao tags;
    private final ModerationService moderation;
    private static final Set<String> EDITABLE = Set.of("draft", "rejected", "withdrawn");

    public HrPostServiceImpl(HrPostDao posts, UserDao users, TagDao tags, ModerationService moderation) {
        this.posts = posts; this.users = users; this.tags = tags; this.moderation = moderation;
    }
    private void requireHr(AuthUser hr) {
        if (!"hr".equals(hr.getRole()) || users.findById(hr.getId()).filter(u -> "hr".equals(u.getRole())).isEmpty())
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅 HR 可管理原创帖子");
    }
    private HrPostDO lock(AuthUser hr, Long id, long version) {
        requireHr(hr);
        HrPostDO post = posts.lockOwn(hr.getId(), id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));
        if (post.getVersion() != version) throw new BusinessException(ErrorCode.CONFLICT, "帖子已在其他页面更新，请保留本页内容后读取最新版本");
        return post;
    }
    private void update(HrPostDO post) {
        post.setUpdatedAt(LocalDateTime.now());
        if (posts.update(post) != 1) throw new BusinessException(ErrorCode.CONFLICT, "帖子版本已变化，请重新读取");
        post.setVersion(post.getVersion() + 1);
    }
    private List<TagDO> normalizeTags(List<String> requested) {
        var catalog = tags.findCategorized();
        var result = new LinkedHashMap<Long, TagDO>();
        for (String value : requested) {
            TagDO tag = catalog.stream().filter(t -> t.getName().equals(value.trim()) || t.getSlug().equals(value.trim()))
                    .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARAM, "请选择现有标签"));
            result.put(tag.getId(), tag);
        }
        return new ArrayList<>(result.values());
    }
    private void saveTags(Long id, List<TagDO> selected) {
        posts.clearTags(id);
        for (TagDO tag : selected) posts.linkTag(id, tag.getId());
    }
    private HrPostView view(HrPostDO post) {
        return new HrPostView(post.getId(), post.getTitle(), post.getBody(), posts.tags(post.getId()), post.getStatus(),
                post.getVersion(), post.getCreatedAt(), post.getUpdatedAt(), post.getSubmittedAt());
    }

    @Override
    public PageResult<PublicShareSummary> listPublicShares(Long cursor, int limit) {
        if (cursor != null && cursor <= 0 || limit < 1 || limit > 50) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "帖子翻页参数无效");
        }
        var rows = posts.listPublished(cursor, limit + 1);
        var items = rows.stream().limit(limit)
                .map(post -> new PublicShareSummary(post.getId(), post.getTitle(), excerpt(post.getBody()),
                        posts.tags(post.getId()), publicNickname(post), post.getUpdatedAt()))
                .toList();
        Long nextCursor = rows.size() > limit ? items.get(items.size() - 1).id() : null;
        return new PageResult<>(items, nextCursor);
    }

    @Override
    public PublicShareDetail getPublicShare(Long id) {
        HrPostDO post = posts.findPublished(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "这篇分享暂不可查看"));
        return new PublicShareDetail(post.getId(), post.getTitle(), post.getBody(), posts.tags(post.getId()),
                publicNickname(post), post.getUpdatedAt());
    }

    private String publicNickname(HrPostDO post) {
        String nickname = post.getAuthorNickname();
        return nickname == null || nickname.isBlank() ? "匿名 HR" : nickname;
    }

    @Override
    public PageResult<HrPostSummary> list(AuthUser hr, String status, Long cursor, int limit) {
        requireHr(hr);
        if (!Set.of("all", "draft", "pending_human", "rejected", "withdrawn", "published", "removed").contains(status)
                || cursor != null && cursor <= 0 || limit < 1 || limit > 50)
            throw new BusinessException(ErrorCode.INVALID_PARAM, "帖子筛选或翻页参数无效");
        var rows = posts.list(hr.getId(), status, cursor, limit + 1);
        var page = rows.stream().limit(limit).toList();
        return new PageResult<>(page.stream().map(p -> new HrPostSummary(p.getId(), p.getTitle(), excerpt(p.getBody()),
                posts.tags(p.getId()), p.getStatus(), p.getVersion(), p.getCreatedAt(), p.getUpdatedAt(), p.getSubmittedAt())).toList(),
                rows.size() > limit ? page.get(page.size() - 1).getId() : null);
    }
    private String excerpt(String body) {
        var flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() <= 160 ? flat : flat.substring(0, 160) + "…";
    }
    @Override
    public HrPostView get(AuthUser hr, Long id) {
        requireHr(hr);
        return view(posts.findOwn(hr.getId(), id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在")));
    }
    @Override @Transactional
    public HrPostView createDraft(AuthUser hr, CreateDraftRequest request) {
        // Lock before the first snapshot read so a waiting retry sees the draft committed by its predecessor.
        if (!"hr".equals(hr.getRole()) || !"hr".equals(posts.lockAuthorRole(hr.getId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅 HR 可管理原创帖子");
        }
        var existing = posts.findByClientKey(hr.getId(), request.clientKey());
        if (existing.isPresent()) return existingDraft(existing.get());
        var selected = normalizeTags(request.tags());
        HrPostDO post = new HrPostDO();
        post.setAuthorId(hr.getId()); post.setClientKey(request.clientKey()); post.setTitle(request.title()); post.setBody(request.body());
        post.setStatus("draft"); post.setVersion(1); post.setCreatedAt(LocalDateTime.now()); post.setUpdatedAt(post.getCreatedAt());
        posts.insert(post);
        saveTags(post.getId(), selected);
        return view(post);
    }
    private HrPostView existingDraft(HrPostDO post) {
        if ("deleted".equals(post.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "该草稿已删除，请新建帖子");
        return view(post);
    }
    @Override @Transactional
    public HrPostView saveDraft(AuthUser hr, Long id, SaveDraftRequest request) {
        HrPostDO post = lock(hr, id, request.version());
        if (!EDITABLE.contains(post.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "请先撤回待审或已发布的帖子，再修改内容");
        var selected = normalizeTags(request.tags());
        post.setTitle(request.title()); post.setBody(request.body()); post.setStatus("draft");
        update(post); saveTags(post.getId(), selected);
        return view(post);
    }
    @Override @Transactional
    public HrPostView submit(AuthUser hr, Long id, long version) {
        HrPostDO post = lock(hr, id, version);
        if (!EDITABLE.contains(post.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "当前帖子不可重复提交");
        if (post.getTitle().isBlank() || post.getBody().isBlank()) throw new BusinessException(ErrorCode.INVALID_PARAM, "提交审核前请填写标题和正文");
        String decision = moderation.decide(post.getTitle() + "\n" + post.getBody());
        // Phase 1 has no publication or operator entry: all non-blocked submissions await review.
        post.setStatus("rejected".equals(decision) ? "rejected" : "pending_human");
        post.setSubmittedAt(LocalDateTime.now()); update(post);
        moderation.recordMachine("hr_post", id, post.getStatus());
        return view(post);
    }
    @Override @Transactional
    public HrPostView withdraw(AuthUser hr, Long id, long version) {
        HrPostDO post = lock(hr, id, version);
        if (!Set.of("pending_human", "published").contains(post.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "当前帖子无需撤回");
        post.setStatus("withdrawn"); update(post);
        return view(post);
    }
    @Override @Transactional
    public void deleteDraft(AuthUser hr, Long id, long version) {
        HrPostDO post = lock(hr, id, version);
        if (!"draft".equals(post.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "仅草稿可以删除，其他内容请使用撤回");
        post.setStatus("deleted"); post.setTitle(""); post.setBody(""); update(post); posts.clearTags(id);
    }
}
