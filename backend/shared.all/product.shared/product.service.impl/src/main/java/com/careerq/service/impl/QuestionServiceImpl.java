package com.careerq.service.impl;

import com.careerq.answer.AnswerDtos.AnswerView;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.PageResult;
import com.careerq.common.UserBrief;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.InteractionDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dao.TagDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.AnswerDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.dataobject.TagDO;
import com.careerq.followup.FollowupDtos.FollowupView;
import com.careerq.question.QuestionDtos.CreateRequest;
import com.careerq.question.QuestionDtos.QuestionDetail;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.question.QuestionDtos.ViewerState;
import com.careerq.security.AuthUser;
import com.careerq.service.FollowupService;
import com.careerq.service.ModerationService;
import com.careerq.service.QuestionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class QuestionServiceImpl implements QuestionService {

    private static final String PUBLIC = "public";
    private static final String PUBLISHED = "published";

    private final QuestionDao questionDao;
    private final AnswerDao answerDao;
    private final TagDao tagDao;
    private final UserDao userDao;
    private final ModerationService moderationService;
    private final FollowupService followupService;
    private final InteractionDao interactionDao;

    public QuestionServiceImpl(QuestionDao questionDao,
                               AnswerDao answerDao,
                               TagDao tagDao,
                               UserDao userDao,
                               ModerationService moderationService,
                               FollowupService followupService,
                               InteractionDao interactionDao) {
        this.questionDao = questionDao;
        this.answerDao = answerDao;
        this.tagDao = tagDao;
        this.userDao = userDao;
        this.moderationService = moderationService;
        this.followupService = followupService;
        this.interactionDao = interactionDao;
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
        question.setStatus(moderation);
        questionDao.insert(question);
        linkTags(question.getId(), request.tags());
        if (!"published".equals(moderation)) {
            moderationService.recordMachine("question", question.getId(), moderation);
        }
        return toView(question);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<QuestionView> list(String tag, Long cursor, int limit) {
        String tagFilter = tag == null || tag.isBlank() ? null : tag;
        return listPage(PUBLIC, PUBLISHED, tagFilter, false, cursor, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<QuestionView> mine(AuthUser author, String scope, Long cursor, int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        String scopeFilter = scope == null || scope.isBlank() ? PUBLIC : normalizeScope(scope);
        List<QuestionDO> rows = questionDao.listByAuthor(author.getId(), scopeFilter, cursor, size + 1);
        boolean hasMore = rows.size() > size;
        List<QuestionDO> page = hasMore ? rows.subList(0, size) : rows;
        List<QuestionView> items = page.stream().map(this::toView).toList();
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        return new PageResult<>(items, nextCursor);
    }
    @Override
    @Transactional(readOnly = true)
    public QuestionDetail detail(AuthUser current, Long id) {
        QuestionDO question = questionDao.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));

        boolean isAuthor = current != null && current.getId().equals(question.getAuthorId());
        if (!PUBLIC.equals(question.getScope()) || (!PUBLISHED.equals(question.getStatus()) && !isAuthor)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在");
        }

        Long viewerId = current == null ? null : current.getId();
        List<AnswerView> answers = answerDao.findVisible(question.getId(), viewerId)
                .stream().map(answer -> toAnswerView(answer, viewerId)).toList();
        List<FollowupView> followups = PUBLISHED.equals(question.getStatus())
                ? followupService.list(current, question.getId())
                : List.of();
        ViewerState viewer = viewerId == null
                ? new ViewerState(false, false, false)
                : new ViewerState(
                        interactionDao.exists(viewerId, "question", question.getId(), "like"),
                        interactionDao.exists(viewerId, "question", question.getId(), "useful"),
                        interactionDao.exists(viewerId, "question", question.getId(), "favorite"));
        return new QuestionDetail(toView(question), answers, followups, viewer);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<QuestionView> hrQuestionPool(Long cursor, int limit) {
        return listPage(PUBLIC, PUBLISHED, null, true, cursor, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<QuestionView> hrAnsweredQuestions(AuthUser hr, Long cursor, int limit) {
        int size = Math.clamp(limit, 1, 50);
        List<QuestionDO> rows = questionDao.listPublicAnsweredByHr(hr.getId(), cursor, size + 1);
        boolean hasNext = rows.size() > size;
        List<QuestionView> items = rows.stream().limit(size).map(this::toView).toList();
        return new PageResult<>(items, hasNext ? rows.get(size - 1).getId() : null);
    }

    private PageResult<QuestionView> listPage(String scope, String status, String tag,
                                              boolean unanswered, Long cursor, int limit) {
        int size = Math.clamp(limit, 1, 50);
        List<QuestionDO> rows = questionDao.list(scope, status, cursor, tag, unanswered, size + 1);
        boolean hasNext = rows.size() > size;
        List<QuestionView> items = rows.stream()
                .limit(size).map(this::toView).toList();
        return new PageResult<>(items, hasNext ? rows.get(size - 1).getId() : null);
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return PUBLIC;
        }
        String value = scope.trim().toLowerCase();
        if (!PUBLIC.equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "目前仅支持公开提问");
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

    private QuestionView toView(QuestionDO question) {
        return new QuestionView(
                question.getId(),
                question.getScope(),
                question.getStatus(),
                question.getTitle(),
                question.getBody(),
                tagDao.findNamesByQuestionId(question.getId()),
                authorOf(question.getAuthorId()),
                question.getCreatedAt(),
                question.getAnswerCount(),
                question.getUsefulCount(),
                question.getLikeCount(),
                question.getFirstAnswerAt());
    }

    private AnswerView toAnswerView(AnswerDO answer, Long viewerId) {
        boolean liked = viewerId != null
                && interactionDao.exists(viewerId, "answer", answer.getId(), "like");
        boolean usefuled = viewerId != null
                && interactionDao.exists(viewerId, "answer", answer.getId(), "useful");
        return new AnswerView(
                answer.getId(),
                answer.getQuestionId(),
                answer.getBody(),
                answer.getLengthType(),
                answer.getStatus(),
                authorOf(answer.getAuthorId()),
                answer.getLikeCount(),
                answer.getUsefulCount(),
                liked,
                usefuled,
                answer.getCreatedAt());
    }

    private UserBrief authorOf(Long userId) {
        return userDao.findById(userId)
                .map(user -> new UserBrief(user.getId(), user.getNickname()))
                .orElse(new UserBrief(userId, "用户"));
    }
}
