package com.careerq.question;

import com.careerq.answer.Answer;
import com.careerq.answer.AnswerDtos.AnswerView;
import com.careerq.answer.AnswerRepository;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.PageResult;
import com.careerq.common.UserBrief;
import com.careerq.moderation.ModerationService;
import com.careerq.question.QuestionDtos.CreateRequest;
import com.careerq.question.QuestionDtos.QuestionDetail;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.security.AuthUser;
import com.careerq.tag.Tag;
import com.careerq.tag.TagRepository;
import com.careerq.user.User;
import com.careerq.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuestionService {

    private static final String PUBLIC = "public";
    private static final String PRIVATE = "private";
    private static final String PUBLISHED = "published";

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final ModerationService moderationService;

    public QuestionService(QuestionRepository questionRepository,
                           AnswerRepository answerRepository,
                           TagRepository tagRepository,
                           UserRepository userRepository,
                           ModerationService moderationService) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.moderationService = moderationService;
    }

    @Transactional
    public QuestionView create(AuthUser author, CreateRequest request) {
        Question question = new Question();
        question.setScope(normalizeScope(request.scope()));
        question.setAuthorId(author.getId());
        question.setTitle(request.title().trim());
        question.setBody(request.body());
        question.setStatus(moderationService.decide(request.title() + "\n" + request.body()));
        question.setTags(resolveTags(request.tags()));
        questionRepository.save(question);
        return toView(question);
    }

    @Transactional(readOnly = true)
    public PageResult<QuestionView> list(String tag, Long cursor, int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
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
        List<QuestionView> items = slice.getContent().stream().map(this::toView).toList();
        Long nextCursor = slice.hasNext() && !items.isEmpty()
                ? slice.getContent().get(slice.getContent().size() - 1).getId()
                : null;
        return new PageResult<>(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public QuestionDetail detail(AuthUser current, Long id) {        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        if (PRIVATE.equals(question.getScope())) {
            boolean isAuthor = current != null && current.getId().equals(question.getAuthorId());
            boolean isOperator = current != null && "operator".equals(current.getRole());
            if (!isAuthor && !isOperator) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该私密问题");
            }
        } else if (!PUBLISHED.equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在");
        }
        List<AnswerView> answers = answerRepository
                .findByQuestionIdAndStatusOrderByIdAsc(question.getId(), PUBLISHED)
                .stream().map(this::toAnswerView).toList();
        return new QuestionDetail(toView(question), answers);
    }

    @Transactional(readOnly = true)
    public PageResult<QuestionView> hrQuestionPool(Long cursor, int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, size);
        Slice<Question> slice = cursor == null
                ? questionRepository.findByScopeAndStatusAndAnswerCountOrderByIdDesc(PUBLIC, PUBLISHED, 0, pageable)
                : questionRepository.findByScopeAndStatusAndAnswerCountAndIdLessThanOrderByIdDesc(
                        PUBLIC, PUBLISHED, 0, cursor, pageable);
        List<QuestionView> items = slice.getContent().stream().map(this::toView).toList();
        Long nextCursor = slice.hasNext() && !items.isEmpty()
                ? slice.getContent().get(slice.getContent().size() - 1).getId()
                : null;
        return new PageResult<>(items, nextCursor);
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

    private QuestionView toView(Question question) {
        return new QuestionView(
                question.getId(),
                question.getScope(),
                question.getStatus(),
                question.getTitle(),
                question.getBody(),
                question.getTags().stream().map(Tag::getName).toList(),
                authorOf(question.getAuthorId()),
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
