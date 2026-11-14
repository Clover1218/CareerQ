package com.careerq.question;

import com.careerq.answer.Answer;
import com.careerq.answer.AnswerDtos.AnswerView;
import com.careerq.answer.AnswerRepository;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.PageResult;
import com.careerq.common.UserBrief;
import com.careerq.followup.FollowupService;
import com.careerq.followup.FollowupService.FollowupView;
import com.careerq.moderation.ModerationService;
import com.careerq.publicpost.PublicPost;
import com.careerq.publicpost.PublicPostRepository;
import com.careerq.question.QuestionDtos.CreateRequest;
import com.careerq.question.QuestionDtos.QuestionDetail;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.security.AuthUser;
import com.careerq.tag.Tag;
import com.careerq.tag.TagRepository;
import com.careerq.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class QuestionService {

    private static final String PUBLIC = "public";
    private static final String PRIVATE = "private";
    private static final String PUBLISHED = "published";
    private static final String OPEN = "open";
    private static final String ASSIGNED = "assigned";
    private static final String CLOSED = "closed";
    private static final String PUBLIC_PENDING = "public_pending";
    private static final String ANONYMOUS_NICKNAME = "匿名同学";

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final ModerationService moderationService;
    private final FollowupService followupService;
    private final PublicPostRepository publicPostRepository;

    public QuestionService(QuestionRepository questionRepository,
                           AnswerRepository answerRepository,
                           TagRepository tagRepository,
                           UserRepository userRepository,
                           ModerationService moderationService,
                           FollowupService followupService,
                           PublicPostRepository publicPostRepository) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.moderationService = moderationService;
        this.followupService = followupService;
        this.publicPostRepository = publicPostRepository;
    }

    @Transactional
    public QuestionView create(AuthUser author, CreateRequest request) {
        String scope = normalizeScope(request.scope());
        String moderation = moderationService.decide(request.title() + "\n" + request.body());

        Question question = new Question();
        question.setScope(scope);
        question.setAuthorId(author.getId());
        question.setTitle(request.title().trim());
        question.setBody(request.body());
        question.setStatus(PRIVATE.equals(scope) && "published".equals(moderation) ? OPEN : moderation);
        question.setTags(resolveTags(request.tags()));
        questionRepository.save(question);
        return toView(question, false);
    }

    @Transactional(readOnly = true)
    public PageResult<QuestionView> list(String tag, Long cursor, int limit) {
        int size = clamp(limit);
        Pageable pageable = PageRequest.of(0, size);
        Slice<Question> slice;
        if (tag != null && !tag.isBlank()) {
            slice = cursor == null
                    ? questionRepository.findPublishedByTag(PUBLIC, PUBLISHED, tag, pageable)
                    : questionRepository.findPublishedByTagBefore(PUBLIC, PUBLISHED, tag, cursor, pageable);
        } else {
            slice = cursor == null
                    ? questionRepository.findByScopeAndStatusOrderByIdDesc(PUBLIC, PUBLISHED, pageable)
                    : questionRepository.findByScopeAndStatusAndIdLessThanOrderByIdDesc(PUBLIC, PUBLISHED, cursor, pageable);
        }
        return pageOf(slice);
    }

    @Transactional(readOnly = true)
    public QuestionDetail detail(AuthUser current, Long id) {
        Question question = questionRepository.findById(id)
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

        List<AnswerView> answers = answerRepository
                .findByQuestionIdAndStatusOrderByIdAsc(question.getId(), PUBLISHED)
                .stream().map(this::toAnswerView).toList();
        List<FollowupView> followups = followupService.list(current, question.getId());
        return new QuestionDetail(toView(question, maskAuthor), answers, followups);
    }

    @Transactional(readOnly = true)
    public PageResult<QuestionView> hrQuestionPool(Long cursor, int limit) {
        Pageable pageable = PageRequest.of(0, clamp(limit));
        Slice<Question> slice = cursor == null
                ? questionRepository.findByScopeAndStatusAndAnswerCountOrderByIdDesc(PUBLIC, PUBLISHED, 0, pageable)
                : questionRepository.findByScopeAndStatusAndAnswerCountAndIdLessThanOrderByIdDesc(
                        PUBLIC, PUBLISHED, 0, cursor, pageable);
        return pageOf(slice);
    }

    /** HR 私密待认领池：只返回 open 状态的私密问题，作者信息匿名化。 */
    @Transactional(readOnly = true)
    public PageResult<QuestionView> hrPrivatePool(Long cursor, int limit) {
        Pageable pageable = PageRequest.of(0, clamp(limit));
        Slice<Question> slice = cursor == null
                ? questionRepository.findByScopeAndStatusOrderByIdDesc(PRIVATE, OPEN, pageable)
                : questionRepository.findByScopeAndStatusAndIdLessThanOrderByIdDesc(PRIVATE, OPEN, cursor, pageable);
        List<QuestionView> items = slice.getContent().stream()
                .map(question -> toView(question, true))
                .toList();
        Long nextCursor = slice.hasNext() && !items.isEmpty()
                ? slice.getContent().get(slice.getContent().size() - 1).getId()
                : null;
        return new PageResult<>(items, nextCursor);
    }

    @Transactional
    public QuestionView claim(AuthUser hr, Long id) {
        Question question = requireOwnedPrivate(id);
        if (!OPEN.equals(question.getStatus()) || question.getAssignedHrId() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该问题已被认领或不可认领");
        }
        question.setAssignedHrId(hr.getId());
        question.setStatus(ASSIGNED);
        questionRepository.save(question);
        return toView(question, true);
    }

    @Transactional
    public QuestionView close(AuthUser author, Long id) {
        Question question = requireOwnedPrivate(id);
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
        questionRepository.save(question);
        return toView(question, false);
    }

    /** 关闭后的私密问答脱敏公开：生成待审核的 public_post。 */
    @Transactional
    public QuestionView publish(AuthUser author, Long id) {
        Question question = requireOwnedPrivate(id);
        if (!author.getId().equals(question.getAuthorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有提问者可以申请公开");
        }
        if (!CLOSED.equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先关闭问题再申请公开");
        }
        List<Answer> answers = answerRepository
                .findByQuestionIdAndStatusOrderByIdAsc(question.getId(), PUBLISHED);
        if (answers.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "该问题还没有回答，无法公开");
        }
        String hrAnswer = answers.get(answers.size() - 1).getBody();

        PublicPost post = new PublicPost();
        post.setOriginalQuestionId(question.getId());
        post.setTitle(question.getTitle());
        post.setQuestionBody(question.getBody());
        post.setHrAnswer(hrAnswer);
        post.setAnonymized(true);
        post.setStatus("pending_human");
        publicPostRepository.save(post);

        question.setStatus(PUBLIC_PENDING);
        questionRepository.save(question);
        return toView(question, false);
    }

    private Question requireOwnedPrivate(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        if (!PRIVATE.equals(question.getScope())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该接口只适用于私密问题");
        }
        return question;
    }

    private PageResult<QuestionView> pageOf(Slice<Question> slice) {
        List<QuestionView> items = slice.getContent().stream().map(question -> toView(question, false)).toList();
        Long nextCursor = slice.hasNext() && !items.isEmpty()
                ? slice.getContent().get(slice.getContent().size() - 1).getId()
                : null;
        return new PageResult<>(items, nextCursor);
    }

    private int clamp(int limit) {
        return Math.min(Math.max(limit, 1), 50);
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

    private Set<Tag> resolveTags(List<String> names) {
        Set<Tag> tags = new LinkedHashSet<>();
        if (names == null) {
            return tags;
        }
        for (String raw : names) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String name = raw.trim();
            String slug = name.toLowerCase();
            Tag tag = tagRepository.findBySlug(slug).orElseGet(() -> tagRepository.save(Tag.of(name, slug)));
            tags.add(tag);
        }
        return tags;
    }

    private QuestionView toView(Question question, boolean maskAuthor) {
        return new QuestionView(
                question.getId(),
                question.getScope(),
                question.getStatus(),
                question.getTitle(),
                question.getBody(),
                question.getTags().stream().map(Tag::getName).toList(),
                maskAuthor ? new UserBrief(question.getAuthorId(), ANONYMOUS_NICKNAME) : authorOf(question.getAuthorId()),
                question.getCreatedAt(),
                question.getAnswerCount(),
                question.getUsefulCount(),
                question.getFirstAnswerAt());
    }

    private AnswerView toAnswerView(Answer answer) {
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
        return userRepository.findById(userId)
                .map(user -> new UserBrief(user.getId(), user.getNickname()))
                .orElse(new UserBrief(userId, "用户"));
    }
}
