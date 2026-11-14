package com.careerq.answer;

import com.careerq.answer.AnswerDtos.AnswerView;
import com.careerq.answer.AnswerDtos.CreateRequest;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.UserBrief;
import com.careerq.moderation.ModerationService;
import com.careerq.question.Question;
import com.careerq.question.QuestionRepository;
import com.careerq.security.AuthUser;
import com.careerq.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AnswerService {

    private static final String PUBLISHED = "published";

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final ModerationService moderationService;

    public AnswerService(AnswerRepository answerRepository,
                         QuestionRepository questionRepository,
                         UserRepository userRepository,
                         ModerationService moderationService) {
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.moderationService = moderationService;
    }

    @Transactional
    public AnswerView create(AuthUser hr, Long questionId, CreateRequest request) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        if ("private".equals(question.getScope())) {
            boolean assignedToMe = hr.getId().equals(question.getAssignedHrId());
            if (!assignedToMe || !"assigned".equals(question.getStatus())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "该私密问题未指派给你");
            }
        } else if (!PUBLISHED.equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "问题当前不可回答");
        }

        Answer answer = new Answer();
        answer.setQuestionId(questionId);
        answer.setAuthorId(hr.getId());
        answer.setBody(request.body());
        answer.setLengthType(normalizeLengthType(request.lengthType()));
        answer.setStatus(moderationService.decide(request.body()));
        answerRepository.save(answer);

        if (PUBLISHED.equals(answer.getStatus())) {
            question.setAnswerCount(question.getAnswerCount() + 1);
            if (question.getFirstAnswerAt() == null) {
                question.setFirstAnswerAt(LocalDateTime.now());
            }
            questionRepository.save(question);
        }
        return toView(answer);
    }

    public AnswerView toView(Answer answer) {
        return new AnswerView(
                answer.getId(),
                answer.getQuestionId(),
                answer.getBody(),
                answer.getLengthType(),
                answer.getStatus(),
                userRepository.findById(answer.getAuthorId())
                        .map(user -> new UserBrief(user.getId(), user.getNickname()))
                        .orElse(new UserBrief(answer.getAuthorId(), "HR")),
                answer.getLikeCount(),
                answer.getUsefulCount(),
                answer.getCreatedAt());
    }

    private String normalizeLengthType(String lengthType) {
        if (lengthType == null || lengthType.isBlank()) {
            return "detailed";
        }
        String value = lengthType.trim().toLowerCase();
        if (!"one_liner".equals(value) && !"detailed".equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "lengthType 只能是 one_liner 或 detailed");
        }
        return value;
    }
}
