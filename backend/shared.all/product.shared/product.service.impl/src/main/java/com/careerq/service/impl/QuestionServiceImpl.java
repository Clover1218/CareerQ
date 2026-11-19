package com.careerq.service.impl;

import com.careerq.answer.AnswerDtos.AnswerView;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.PageResult;
import com.careerq.common.UserBrief;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.InteractionDao;
import com.careerq.dao.PublicPostDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dao.TagDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.AnswerDO;
import com.careerq.dataobject.PublicPostDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.dataobject.QuestionTagDO;
import com.careerq.dataobject.TagDO;
import com.careerq.feed.FeedPageCursor;
import com.careerq.feed.FeedScorer;
import com.careerq.feed.FeedSeekCursor;
import com.careerq.feed.FeedTuning;
import com.careerq.followup.FollowupDtos.FollowupView;
import com.careerq.question.QuestionDtos.CreateRequest;
import com.careerq.question.QuestionDtos.FeedPage;
import com.careerq.question.QuestionDtos.FeedQuery;
import com.careerq.question.QuestionDtos.FeedSort;
import com.careerq.question.QuestionDtos.HrAnswerSummary;
import com.careerq.question.QuestionDtos.HrAnsweredQuestionView;
import com.careerq.question.QuestionDtos.QuestionContentView;
import com.careerq.question.QuestionDtos.QuestionDetail;
import com.careerq.question.QuestionDtos.QuestionFeedItem;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.question.QuestionDtos.ThreadItemView;
import com.careerq.question.QuestionDtos.ThreadView;
import com.careerq.question.QuestionDtos.ViewerState;
import com.careerq.security.AuthUser;
import com.careerq.service.FollowupService;
import com.careerq.service.ModerationService;
import com.careerq.service.QuestionService;
import com.careerq.service.TagAffinityService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private static final int MAX_KEYWORD_LENGTH = 50;
    private static final int MAX_TAGS = 10;
    private static final int MAX_FILTER_RANGE_DAYS = 366;

    private final QuestionDao questionDao;
    private final AnswerDao answerDao;
    private final TagDao tagDao;
    private final UserDao userDao;
    private final ModerationService moderationService;
    private final FollowupService followupService;
    private final PublicPostDao publicPostDao;
    private final InteractionDao interactionDao;
    private final TagAffinityService tagAffinityService;

    public QuestionServiceImpl(QuestionDao questionDao,
                               AnswerDao answerDao,
                               TagDao tagDao,
                               UserDao userDao,
                               ModerationService moderationService,
                               FollowupService followupService,
                               PublicPostDao publicPostDao,
                               InteractionDao interactionDao,
                               TagAffinityService tagAffinityService) {
        this.questionDao = questionDao;
        this.answerDao = answerDao;
        this.tagDao = tagDao;
        this.userDao = userDao;
        this.moderationService = moderationService;
        this.followupService = followupService;
        this.publicPostDao = publicPostDao;
        this.interactionDao = interactionDao;
        this.tagAffinityService = tagAffinityService;
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
        return toView(question, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<QuestionFeedItem> list(String tag, Long cursor, int limit) {
        String tagFilter = tag == null || tag.isBlank() ? null : tag;
        int size = Math.clamp(limit, 1, 50);
        List<QuestionDO> rows = questionDao.list(PUBLIC, PUBLISHED, cursor, tagFilter, false, size + 1);
        boolean hasNext = rows.size() > size;
        List<QuestionFeedItem> items = rows.stream().limit(size).map(this::toFeedItem).toList();
        return new PageResult<>(items, hasNext ? rows.get(size - 1).getId() : null);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedPage feed(AuthUser viewer, String tag, String cursor, int limit) {
        List<String> tags = tag == null || tag.isBlank() ? List.of() : List.of(tag.trim());
        return feed(viewer, new FeedQuery(null, tags, true, null, null, null, FeedSort.RECOMMEND), cursor, limit);
    }

    /**
     * 统一公开列表：latest 走 (createdAt, id) seek 分页；recommend/hot 在固定快照上
     * 重算完整排序再按页码切片。筛选条件在候选召回阶段生效。
     */
    @Override
    @Transactional(readOnly = true)
    public FeedPage feed(AuthUser viewer, FeedQuery query, String cursor, int limit) {
        FeedFilter filter = normalizeFilter(query);
        int size = Math.clamp(limit, 1, 50);
        if (filter.sort() == FeedSort.LATEST) {
            return feedLatest(viewer, filter, cursor, size);
        }
        return feedScored(viewer, filter, cursor, size);
    }

    private FeedPage feedLatest(AuthUser viewer, FeedFilter filter, String cursor, int size) {
        LocalDateTime lastCreatedAt = null;
        long lastId = 0;
        if (cursor != null && !cursor.isBlank()) {
            FeedSeekCursor page = FeedSeekCursor.decode(cursor);
            requireCursor(page.sort(), page.filterHash(), filter);
            lastCreatedAt = page.lastCreatedAt();
            lastId = page.lastId();
        }
        Long viewerId = viewer == null ? null : viewer.getId();
        List<QuestionDO> rows = questionDao.listLatest(PUBLIC, PUBLISHED, filter.keyword(), filter.tags(),
                filter.tags().size(), filter.matchAll(), filter.from(), filter.to(),
                lastCreatedAt, lastId, viewerId, size + 1);
        boolean hasMore = rows.size() > size;
        List<QuestionDO> pageRows = hasMore ? rows.subList(0, size) : rows;
        String nextCursor = null;
        if (hasMore) {
            QuestionDO last = pageRows.get(pageRows.size() - 1);
            nextCursor = new FeedSeekCursor("latest", filter.hash(), last.getCreatedAt(), last.getId()).encode();
        }
        return new FeedPage(toFeedItems(pageRows), nextCursor);
    }

    private FeedPage feedScored(AuthUser viewer, FeedFilter filter, String cursor, int size) {
        String sortName = filter.sort() == FeedSort.HOT ? "hot" : "recommend";
        FeedPageCursor page;
        if (cursor == null || cursor.isBlank()) {
            page = FeedPageCursor.first(sortName, filter.hash(), questionDao.findMaxId(PUBLIC, PUBLISHED));
        } else {
            page = FeedPageCursor.decode(cursor);
            requireCursor(page.sort(), page.filterHash(), filter);
        }
        if (page.page() >= FeedTuning.MAX_PAGE) {
            return new FeedPage(List.of(), null);
        }

        Long viewerId = viewer == null ? null : viewer.getId();
        LocalDateTime snapshotAt = page.snapshotTime();
        List<QuestionDO> candidates = loadCandidates(page, filter, viewerId, size);
        if (candidates.isEmpty()) {
            return new FeedPage(List.of(), null);
        }

        Map<Long, List<QuestionTagDO>> tagsByQuestion = loadQuestionTags(candidates);
        List<ScoredQuestion> ordered = filter.sort() == FeedSort.HOT
                ? scoreHot(candidates, tagsByQuestion, snapshotAt)
                : scoreRecommend(candidates, tagsByQuestion, viewerId, snapshotAt, page.seed(), size);
        int start = page.page() * size;
        if (start >= ordered.size()) {
            return new FeedPage(List.of(), null);
        }
        int end = Math.min(start + size, ordered.size());
        List<QuestionFeedItem> items = ordered.subList(start, end).stream()
                .map(item -> toFeedItem(item.question(),
                        item.tags().stream().map(QuestionTagDO::getName).toList(), item.matchedTags()))
                .toList();
        boolean hasMore = end < ordered.size() && page.page() + 1 < FeedTuning.MAX_PAGE;
        return new FeedPage(items, hasMore ? page.next().encode() : null);
    }

    private void requireCursor(String sort, long filterHash, FeedFilter filter) {
        String expected = filter.sort() == FeedSort.HOT ? "hot"
                : filter.sort() == FeedSort.LATEST ? "latest" : "recommend";
        if (!expected.equals(sort) || filterHash != filter.hash()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "游标与当前筛选条件不匹配");
        }
    }

    private FeedFilter normalizeFilter(FeedQuery query) {
        FeedSort sort = query == null || query.sort() == null ? FeedSort.RECOMMEND : query.sort();
        String keyword = query == null ? null : trimToNull(query.q());
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_PARAM,
                    "搜索关键词不能超过 " + MAX_KEYWORD_LENGTH + " 字");
        }
        if (keyword != null) {
            keyword = keyword.toLowerCase(Locale.ROOT);
        }
        List<String> tags = normalizeTags(query == null ? null : query.tags());
        if (tags.size() > MAX_TAGS) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "标签筛选最多支持 " + MAX_TAGS + " 个");
        }
        LocalDateTime from = query == null ? null : query.from();
        LocalDateTime to = query == null ? null : query.to();
        String period = null;
        if (from == null && to == null) {
            period = normalizePeriod(query == null ? null : query.period());
            from = periodStart(period);
        }
        if (from != null && to != null) {
            if (!from.isBefore(to)) {
                throw new BusinessException(ErrorCode.INVALID_PARAM, "开始时间必须早于结束时间");
            }
            if (Duration.between(from, to).toDays() > MAX_FILTER_RANGE_DAYS) {
                throw new BusinessException(ErrorCode.INVALID_PARAM,
                        "时间范围不能超过 " + MAX_FILTER_RANGE_DAYS + " 天");
            }
        }
        boolean matchAll = query == null || query.matchAll();
        // period 用原始枚举参与指纹，避免滚动窗口每次请求算出不同的 from 导致翻页游标失效
        long hash = Objects.hash(sort, keyword, tags, matchAll, period,
                period == null ? from : null, period == null ? to : null);
        return new FeedFilter(sort, keyword, tags, matchAll, from, to, hash);
    }

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        String value = period.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "all" -> null;
            case "day", "week", "month" -> value;
            default -> throw new BusinessException(ErrorCode.INVALID_PARAM,
                    "period 只能是 day、week、month 或 all");
        };
    }

    private LocalDateTime periodStart(String period) {
        if (period == null) {
            return null;
        }
        return switch (period) {
            case "day" -> LocalDateTime.now().minusHours(24);
            case "week" -> LocalDateTime.now().minusDays(7);
            case "month" -> LocalDateTime.now().minusDays(30);
            default -> null;
        };
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> normalizeTags(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null) {
                continue;
            }
            for (String part : value.split(",")) {
                String tag = part.trim();
                if (!tag.isEmpty()) {
                    unique.add(tag);
                }
            }
        }
        return List.copyOf(unique);
    }

    private List<QuestionDO> loadCandidates(FeedPageCursor page, FeedFilter filter, Long viewerId, int size) {
        boolean hot = filter.sort() == FeedSort.HOT;
        LocalDateTime since = filter.from() != null
                ? filter.from()
                : page.snapshotTime().minusDays(hot
                        ? FeedTuning.HOT_CANDIDATE_WINDOW_DAYS
                        : FeedTuning.CANDIDATE_WINDOW_DAYS);
        int limit = hot ? FeedTuning.HOT_CANDIDATE_LIMIT : FeedTuning.CANDIDATE_LIMIT;
        List<QuestionDO> rows = questionDao.listFilteredCandidates(PUBLIC, PUBLISHED, page.maxId(), since,
                filter.to(), filter.keyword(), filter.tags(), filter.tags().size(), filter.matchAll(),
                viewerId, limit);
        if (rows.size() < size && filter.from() == null) {
            // 时间窗口内太少时放宽到不限时间，宁可有内容也不要空 Feed
            rows = questionDao.listFilteredCandidates(PUBLIC, PUBLISHED, page.maxId(), null,
                    filter.to(), filter.keyword(), filter.tags(), filter.tags().size(), filter.matchAll(),
                    viewerId, limit);
        }
        return rows;
    }

    private List<ScoredQuestion> scoreHot(List<QuestionDO> candidates,
                                          Map<Long, List<QuestionTagDO>> tagsByQuestion,
                                          LocalDateTime snapshotAt) {
        return candidates.stream()
                .map(question -> new ScoredQuestion(question,
                        tagsByQuestion.getOrDefault(question.getId(), List.of()),
                        FeedScorer.hot(question, snapshotAt), List.of(), 0))
                .sorted(Comparator
                        .comparingDouble(ScoredQuestion::score).reversed()
                        .thenComparing(Comparator.comparing(
                                (ScoredQuestion item) -> item.question().getCreatedAt()).reversed())
                        .thenComparing(Comparator.comparingLong(
                                (ScoredQuestion item) -> item.question().getId()).reversed()))
                .toList();
    }

    /**
     * 推荐排序：固定快照上重算完整排序。
     * 亲和分来自 user_tag_affinity（已按 30 天半衰期衰减），问题侧按标签求和后压扁。
     */
    private List<ScoredQuestion> scoreRecommend(List<QuestionDO> candidates,
                                                Map<Long, List<QuestionTagDO>> tagsByQuestion,
                                                Long viewerId, LocalDateTime snapshotAt,
                                                long seed, int size) {
        Map<Long, Double> affinity = viewerId == null
                ? Map.of()
                : tagAffinityService.effectiveScores(viewerId, snapshotAt);
        boolean hasAffinity = !affinity.isEmpty();

        List<ScoredQuestion> scored = new ArrayList<>(candidates.size());
        for (QuestionDO question : candidates) {
            List<QuestionTagDO> tags = tagsByQuestion.getOrDefault(question.getId(), List.of());
            double questionAffinity = 0;
            double minAffinity = 0;
            List<QuestionTagDO> positive = new ArrayList<>();
            for (QuestionTagDO tagRow : tags) {
                Double value = affinity.get(tagRow.getTagId());
                if (value == null) {
                    continue;
                }
                questionAffinity += value;
                minAffinity = Math.min(minAffinity, value);
                if (value > 0) {
                    positive.add(tagRow);
                }
            }
            questionAffinity = FeedScorer.clamp(questionAffinity,
                    -FeedTuning.MAX_QUESTION_AFFINITY, FeedTuning.MAX_QUESTION_AFFINITY);
            positive.sort(Comparator.comparingDouble(
                    (QuestionTagDO tagRow) -> affinity.getOrDefault(tagRow.getTagId(), 0.0)).reversed());
            List<String> matchedTags = positive.stream().limit(2).map(QuestionTagDO::getName).toList();
            double score = FeedScorer.score(question, questionAffinity, hasAffinity, seed, snapshotAt);
            scored.add(new ScoredQuestion(question, tags, score, matchedTags, minAffinity));
        }
        scored.sort(Comparator.comparingDouble(ScoredQuestion::score)
                .thenComparingLong((ScoredQuestion item) -> item.question().getId())
                .reversed());

        return diversify(filterNegatives(scored, size));
    }

    private List<QuestionFeedItem> toFeedItems(List<QuestionDO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<QuestionTagDO>> tagsByQuestion = loadQuestionTags(rows);
        return rows.stream()
                .map(question -> toFeedItem(question,
                        tagsByQuestion.getOrDefault(question.getId(), List.of()).stream()
                                .map(QuestionTagDO::getName).toList(),
                        List.of()))
                .toList();
    }

    private Map<Long, List<QuestionTagDO>> loadQuestionTags(List<QuestionDO> questions) {
        List<Long> ids = questions.stream().map(QuestionDO::getId).toList();
        Map<Long, List<QuestionTagDO>> result = new LinkedHashMap<>();
        for (QuestionTagDO row : tagDao.findTagsByQuestionIds(ids)) {
            result.computeIfAbsent(row.getQuestionId(), key -> new ArrayList<>()).add(row);
        }
        return result;
    }

    /** 硬负向过滤带兜底：过滤后不足一页就逐步放宽阈值，最后允许全部通过。 */
    private List<ScoredQuestion> filterNegatives(List<ScoredQuestion> sorted, int size) {
        List<ScoredQuestion> filtered = sorted.stream()
                .filter(item -> item.minAffinity() > FeedTuning.HARD_NEGATIVE_THRESHOLD).toList();
        if (filtered.size() >= size) {
            return new ArrayList<>(filtered);
        }
        List<ScoredQuestion> relaxed = sorted.stream()
                .filter(item -> item.minAffinity() > FeedTuning.HARD_NEGATIVE_THRESHOLD - 4).toList();
        return relaxed.size() >= size ? new ArrayList<>(relaxed) : new ArrayList<>(sorted);
    }

    /** 任意连续 3 条不来自同一一级分类；被挤出的条目按原顺序追加到末尾。 */
    private List<ScoredQuestion> diversify(List<ScoredQuestion> input) {
        if (input.size() < 3) {
            return input;
        }
        List<ScoredQuestion> result = new ArrayList<>(input.size());
        List<ScoredQuestion> deferred = new ArrayList<>();
        String lastCategory = null;
        int run = 0;
        for (ScoredQuestion item : input) {
            String category = primaryCategory(item.tags());
            if (category != null && category.equals(lastCategory) && run >= 2) {
                deferred.add(item);
                continue;
            }
            result.add(item);
            if (category != null && category.equals(lastCategory)) {
                run++;
            } else {
                lastCategory = category;
                run = category == null ? 0 : 1;
            }
        }
        result.addAll(deferred);
        return result;
    }

    private String primaryCategory(List<QuestionTagDO> tags) {
        return tags.stream()
                .filter(tag -> tag.getCategory() != null)
                .max(Comparator.comparingInt(QuestionTagDO::getWeight)
                        .thenComparing(QuestionTagDO::getTagId))
                .map(QuestionTagDO::getCategory)
                .orElse(null);
    }

    private record ScoredQuestion(QuestionDO question, List<QuestionTagDO> tags,
                                  double score, List<String> matchedTags, double minAffinity) {
    }

    private record FeedFilter(FeedSort sort, String keyword, List<String> tags, boolean matchAll,
                              LocalDateTime from, LocalDateTime to, long hash) {
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<QuestionView> mine(AuthUser author, String scope, Long cursor, int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        String scopeFilter = scope == null || scope.isBlank() ? null : normalizeScope(scope);
        List<QuestionDO> rows = questionDao.listByAuthor(author.getId(), scopeFilter, cursor, size + 1);
        boolean hasMore = rows.size() > size;
        List<QuestionDO> page = hasMore ? rows.subList(0, size) : rows;
        List<QuestionView> items = page.stream().map(question -> toView(question, false)).toList();
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        return new PageResult<>(items, nextCursor);
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
            boolean assignedAndVisible = isAssignedHr
                    && Set.of(ASSIGNED, CLOSED, PUBLIC_PENDING, "public_archived", "public_rejected")
                    .contains(question.getStatus());
            if (!isAuthor && !assignedAndVisible && !isOperator) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该私密问题");
            }
            maskAuthor = !isAuthor;
        } else if (!PUBLISHED.equals(question.getStatus())) {
            // 作者需要在自己的提问列表里看到审核中/未通过的公开问题
            boolean isAuthor = current != null && current.getId().equals(question.getAuthorId());
            if (!isAuthor) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在");
            }
        }

        Long viewerId = current == null ? null : current.getId();
        Long ownAnswerAuthor = PRIVATE.equals(question.getScope()) ? viewerId : null;
        List<AnswerView> answers = answerDao.findVisible(question.getId(), ownAnswerAuthor)
                .stream().map(answer -> toAnswerView(answer, viewerId)).toList();
        List<FollowupView> followups = followupService.list(current, question.getId());
        return new QuestionDetail(toView(question, maskAuthor), answers, followups,
                viewerState(current, question.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionContentView content(AuthUser current, Long id) {
        QuestionDO question = requirePublishedPublic(id);
        return new QuestionContentView(toView(question, false), viewerState(current, question.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public ThreadView thread(AuthUser current, Long id) {
        QuestionDO question = requirePublishedPublic(id);
        Long viewerId = current == null ? null : current.getId();
        List<ThreadItemView> items = new ArrayList<>();

        for (AnswerDO answer : answerDao.findVisible(question.getId(), null)) {
            boolean liked = viewerId != null
                    && interactionDao.exists(viewerId, "answer", answer.getId(), "like");
            items.add(new ThreadItemView(
                    "answer-" + answer.getId(),
                    "hr_answer",
                    answer.getId(),
                    answer.getBody(),
                    authorOf(answer.getAuthorId()),
                    true,
                    answer.getStatus(),
                    answer.getLikeCount(),
                    liked,
                    answer.getCreatedAt()));
        }

        for (FollowupView followup : followupService.list(current, question.getId())) {
            items.add(new ThreadItemView(
                    "followup-" + followup.id(),
                    "followup",
                    followup.id(),
                    followup.body(),
                    followup.author(),
                    followup.hr(),
                    followup.status(),
                    0,
                    false,
                    followup.createdAt()));
        }

        items.sort(Comparator
                .comparing(ThreadItemView::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(item -> "hr_answer".equals(item.kind()) ? 0 : 1)
                .thenComparing(ThreadItemView::id));

        int answerCount = question.getAnswerCount();
        int followupCount = question.getFollowupCount();
        return new ThreadView(items, viewerState(current, question.getId()),
                answerCount, followupCount, answerCount + followupCount);
    }

    private QuestionDO requirePublishedPublic(Long id) {
        return questionDao.findById(id)
                .filter(question -> PUBLIC.equals(question.getScope()) && PUBLISHED.equals(question.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
    }

    private ViewerState viewerState(AuthUser current, Long questionId) {
        if (current == null) {
            return new ViewerState(false, false, false, false);
        }
        Long viewerId = current.getId();
        return new ViewerState(
                interactionDao.exists(viewerId, "question", questionId, "like"),
                interactionDao.exists(viewerId, "question", questionId, "useful"),
                interactionDao.exists(viewerId, "question", questionId, "favorite"),
                interactionDao.exists(viewerId, "question", questionId, "dislike"));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<QuestionView> hrQuestionPool(Long cursor, int limit) {
        return listPage(PUBLIC, PUBLISHED, null, true, cursor, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<HrAnsweredQuestionView> hrAnsweredQuestions(AuthUser hr, Long cursor, int limit) {
        int size = Math.clamp(limit, 1, 50);
        List<QuestionDO> rows = questionDao.listPublicAnsweredByHr(hr.getId(), cursor, size + 1);
        boolean hasMore = rows.size() > size;
        List<QuestionDO> page = hasMore ? rows.subList(0, size) : rows;
        List<Long> questionIds = page.stream().map(QuestionDO::getId).toList();
        Map<Long, AnswerDO> latestAnswers = questionIds.isEmpty()
                ? Map.of()
                : answerDao.findLatestByAuthorAndQuestionIds(hr.getId(), questionIds).stream()
                        .collect(java.util.stream.Collectors.toMap(AnswerDO::getQuestionId, answer -> answer));

        List<HrAnsweredQuestionView> items = page.stream()
                .map(question -> {
                    AnswerDO answer = latestAnswers.get(question.getId());
                    if (answer == null) {
                        return null;
                    }
                    return new HrAnsweredQuestionView(toView(question, false),
                            new HrAnswerSummary(answer.getId(), answer.getBody(), answer.getLengthType(),
                                    answer.getStatus(), answer.getCreatedAt()));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        return new PageResult<>(items, nextCursor);
    }

    /** HR 私密待认领池：只返回 open 状态的私密问题，作者信息匿名化。 */
    @Override
    @Transactional(readOnly = true)
    public PageResult<QuestionView> hrPrivatePool(Long cursor, int limit) {
        throw new BusinessException(ErrorCode.INVALID_PARAM, "私密问答已明确不做");
    }

    @Override
    @Transactional
    public QuestionView claim(AuthUser hr, Long id) {
        throw new BusinessException(ErrorCode.INVALID_PARAM, "私密问答已明确不做");
    }

    @Override
    @Transactional
    public QuestionView close(AuthUser author, Long id) {
        return submitPublicPost(author, id, true);
    }

    @Override
    @Transactional
    public QuestionView publish(AuthUser author, Long id) {
        return submitPublicPost(author, id, false);
    }

    private QuestionView submitPublicPost(AuthUser author, Long id, boolean closing) {
        QuestionDO question = requireOwnedPrivate(id);
        if (!author.getId().equals(question.getAuthorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有提问者可以结单或提交公开复审");
        }
        String status = question.getStatus();
        if (PUBLIC_PENDING.equals(status) || "public_archived".equals(status)) {
            return toView(question, false);
        }
        if ("public_rejected".equals(status)) {
            if (closing) {
                return toView(question, false);
            }
            PublicPostDO rejected = publicPostDao.findByOriginalQuestionId(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "公开稿不存在"));
            publicPostDao.updateStatus(rejected.getId(), "pending_human", null);
        } else {
            if (!CLOSED.equals(status) && !(closing && ASSIGNED.equals(status))) {
                throw new BusinessException(ErrorCode.CONFLICT, "问题认领并产生审核通过的回答后才能结单");
            }
            createPublicPost(question);
        }

        if (ASSIGNED.equals(status)) {
            question.setClosedAt(LocalDateTime.now());
        }
        question.setStatus(PUBLIC_PENDING);
        questionDao.updatePrivateState(question);
        return toView(question, false);
    }

    private void createPublicPost(QuestionDO question) {
        if (answerDao.hasPending(question.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "请等待回答审核完成后再结单");
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
        if (!PUBLIC.equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "当前仅支持公开提问，私密问答已明确不做");
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

    private QuestionFeedItem toFeedItem(QuestionDO question) {
        return toFeedItem(question, tagDao.findNamesByQuestionId(question.getId()), List.of());
    }

    private QuestionFeedItem toFeedItem(QuestionDO question, List<String> tags, List<String> matchedTags) {
        int answerCount = question.getAnswerCount();
        int followupCount = question.getFollowupCount();
        return new QuestionFeedItem(
                question.getId(),
                question.getTitle(),
                question.getBody() == null ? "" : question.getBody(),
                tags,
                answerCount,
                followupCount,
                answerCount + followupCount,
                question.getLikeCount(),
                question.getFavoriteCount(),
                question.getCreatedAt(),
                matchedTags);
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
                question.getFollowupCount(),
                question.getUsefulCount(),
                question.getLikeCount(),
                question.getFavoriteCount(),
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
