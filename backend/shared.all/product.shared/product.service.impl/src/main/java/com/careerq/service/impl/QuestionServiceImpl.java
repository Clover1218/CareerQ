package com.careerq.service.impl;

import com.careerq.answer.AnswerDtos.AnswerView;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.PageResult;
import com.careerq.common.UserBrief;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.PublicPostDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dao.TagDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.AnswerDO;
import com.careerq.dataobject.PublicPostDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.dataobject.TagDO;
import com.careerq.followup.FollowupDtos.FollowupView;
import com.careerq.question.QuestionDtos.CreateRequest;
import com.careerq.question.QuestionDtos.QuestionDetail;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.security.AuthUser;
import com.careerq.service.FollowupService;
import com.careerq.service.ModerationService;
import com.careerq.service.QuestionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class QuestionServiceImpl implements QuestionService {

    private static final String PUBLIC = "public";
    private static final String PRIVATE = "private";
    private static final String PUBLISHED = "published";
    private static final String OPEN = "open";
    private static final String ASSIGNED = "assigned";
    private static final String CLOSED = "closed";
    private static final String PUBLIC_PENDING = "public_pending";
    private static final String ANONYMOUS_NICKNAME = "匿名同学";

    private final QuestionDao questionDao;
    private final AnswerDao answerDao;
    private final TagDao tagDao;
    private final UserDao userDao;
    private final ModerationService moderationService;
    private final FollowupService followupService;
    private final PublicPostDao publicPostDao;

    public QuestionServiceImpl(QuestionDao questionDao,
                               AnswerDao answerDao,
                               TagDao tagDao,
                               UserDao userDao,
                               ModerationService moderationService,
                               FollowupService followupService,
                               PublicPostDao publicPostDao) {
        this.questionDao = questionDao;
        this.answerDao = answerDao;
        this.tagDao = tagDao;
        this.userDao = userDao;
        this.moderationService = moderationService;
        this.followupService = followupService;
        this.publicPostDao = publicPostDao;
    }

    @Override
    @Transactional
    public QuestionView create(AuthUser author, CreateRequest request) {
        String scope = normalizeScope(request.scope());
        String moderation = moderationService.decide(request.title() + "\n" + request.body());

        QuestionDO question = new QuestionDO();
        question.setScope(scope);
        question.setAuthorId(author.getId());
        question.setTitle(request.title().trim());
        question.setBody(request.body());
        question.setStatus(PRIVATE.equals(scope) && "published".equals(moderation) ? OPEN : moderation);
        questionDao.insert(question);
        linkTags(question.getId(), request.tags());
        if (!"published".equals(moderation)) {
            moderationService.recordMachine("question", question.getId(), moderation);
        }
        return toView(question, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<QuestionView> list(String tag, Long cursor, int limit) {
        String tagFilter = tag == null || tag.isBlank() ? null : tag;
        return listPage(PUBLIC, PUBLISHED, tagFilter, false, cursor, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionDetail detail(AuthUser current, Long id) {
        QuestionDO question = questionDao.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));

        boolean maskAuthor = false;
        if (PRIVATE.equals(question.getScope())) {
            boolean isAuthor = current != null && current.getId().equals(question.getAuthorId());
            boolean isAssignedHr = current != null && current.getId().equals(question.getAssignedHrId());
            boolean isOperator = current != null
                    && ("operator".equals(current.getRole()) || "admin".equals(current.getRole()));
            if (!isAuthor && !isAssignedHr && !isOperator) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该私密问题");
            }
            maskAuthor = !isAuthor;
        } else if (!PUBLISHED.equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在");
        }

        List<AnswerView> answers = answerDao
                .findByQuestionIdAndStatusOrderByIdAsc(question.getId(), PUBLISHED)
                .stream().map(this::toAnswerView).toList();
        List<FollowupView> followups = followupService.list(current, question.getId());
        return new QuestionDetail(toView(question, maskAuthor), answers, followups);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<QuestionView> hrQuestionPool(Long cursor, int limit) {
        return listPage(PUBLIC, PUBLISHED, null, true, cursor, limit);
    }

    /** HR 私密待认领池：只返回 open 状态的私密问题，作者信息匿名化。 */
    @Override
    @Transactional(readOnly = true)
    public PageResult<QuestionView> hrPrivatePool(Long cursor, int limit) {
        return listPage(PRIVATE, OPEN, null, false, cursor, limit);
    }

    @Override
    @Transactional
    public QuestionView claim(AuthUser hr, Long id) {
        QuestionDO question = requireOwnedPrivate(id);
        if (!OPEN.equals(question.getStatus()) || question.getAssignedHrId() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该问题已被认领或不可认领");
        }
        question.setAssignedHrId(hr.getId());
        question.setStatus(ASSIGNED);
        questionDao.updatePrivateState(question);
        return toView(question, true);
    }

    @Override
    @Transactional
    public QuestionView close(AuthUser author, Long id) {
        QuestionDO question = requireOwnedPrivate(id);
        if (!author.getId().equals(question.getAuthorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有提问者可以关闭问题");
        }
        if (CLOSED.equals(question.getStatus())) {
            return toView(question, false);
        }
        if (!OPEN.equals(question.getStatus()) && !ASSIGNED.equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可关闭");
        }
        question.setStatus(CLOSED);
        question.setClosedAt(LocalDateTime.now());
        questionDao.updatePrivateState(question);
        return toView(question, false);
    }

    /** 关闭后的私密问答脱敏公开：生成待审核的 public_post。 */
    @Override
    @Transactional
    public QuestionView publish(AuthUser author, Long id) {
        QuestionDO question = requireOwnedPrivate(id);
        if (!author.getId().equals(question.getAuthorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有提问者可以申请公开");
        }
        if (!CLOSED.equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先关闭问题再申请公开");
        }
        List<AnswerDO> answers = answerDao
                .findByQuestionIdAndStatusOrderByIdAsc(question.getId(), PUBLISHED);
        if (answers.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "该问题还没有回答，无法公开");
        }
        String hrAnswer = answers.get(answers.size() - 1).getBody();

        PublicPostDO post = new PublicPostDO();
        post.setOriginalQuestionId(question.getId());
        post.setTitle(question.getTitle());
        post.setQuestionBody(question.getBody());
        post.setHrAnswer(hrAnswer);
        post.setAnonymized(true);
        post.setStatus("pending_human");
        publicPostDao.insert(post);
        moderationService.recordMachine("public_post", post.getId(), "pending_human");

        question.setStatus(PUBLIC_PENDING);
        questionDao.updatePrivateState(question);
        return toView(question, false);
    }

    private QuestionDO requireOwnedPrivate(Long id) {
        QuestionDO question = questionDao.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        if (!PRIVATE.equals(question.getScope())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该接口只适用于私密问题");
        }
        return question;
    }

    private PageResult<QuestionView> listPage(String scope, String status, String tag,
                                              boolean unanswered, Long cursor, int limit) {
        int size = Math.clamp(limit, 1, 50);
        List<QuestionDO> rows = questionDao.list(scope, status, cursor, tag, unanswered, size + 1);
        boolean hasNext = rows.size() > size;
        boolean maskAuthor = PRIVATE.equals(scope);
        List<QuestionView> items = rows.stream()
                .limit(size).map(question -> toView(question, maskAuthor)).toList();
        return new PageResult<>(items, hasNext ? rows.get(size - 1).getId() : null);
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return PUBLIC;
        }
        String value = scope.trim().toLowerCase();
        if (!PUBLIC.equals(value) && !PRIVATE.equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "scope 只能是 public 或 private");
        }
        return value;
    }

    private void linkTags(Long questionId, List<String> names) {
        if (names == null) {
            return;
        }
        Set<String> slugs = new LinkedHashSet<>();
        for (String raw : names) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String name = raw.trim();
            String slug = name.toLowerCase();
            if (slugs.add(slug)) {
                TagDO tag = tagDao.findBySlug(slug).orElseGet(() -> createTag(name, slug));
                tagDao.linkToQuestion(questionId, tag.getId());
            }
        }
    }

    private TagDO createTag(String name, String slug) {
        TagDO tag = TagDO.of(name, slug);
        try {
            tagDao.insert(tag);
            return tag;
        } catch (DuplicateKeyException conflict) {
            return tagDao.findBySlugForUpdate(slug).orElseThrow(() -> conflict);
        }
    }

    private QuestionView toView(QuestionDO question, boolean maskAuthor) {
        return new QuestionView(
                question.getId(),
                question.getScope(),
                question.getStatus(),
                question.getTitle(),
                question.getBody(),
                tagDao.findNamesByQuestionId(question.getId()),
                maskAuthor ? new UserBrief(question.getAuthorId(), ANONYMOUS_NICKNAME) : authorOf(question.getAuthorId()),
                question.getCreatedAt(),
                question.getAnswerCount(),
                question.getUsefulCount(),
                question.getFirstAnswerAt());
    }

    private AnswerView toAnswerView(AnswerDO answer) {
        return new AnswerView(
                answer.getId(),
                answer.getQuestionId(),
                answer.getBody(),
                answer.getLengthType(),
                answer.getStatus(),
                authorOf(answer.getAuthorId()),
                answer.getLikeCount(),
                answer.getUsefulCount(),
                answer.getCreatedAt());
    }

    private UserBrief authorOf(Long userId) {
        return userDao.findById(userId)
                .map(user -> new UserBrief(user.getId(), user.getNickname()))
                .orElse(new UserBrief(userId, "用户"));
    }
}
