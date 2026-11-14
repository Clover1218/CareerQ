package com.careerq.interaction;

import com.careerq.answer.Answer;
import com.careerq.answer.AnswerRepository;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.question.Question;
import com.careerq.question.QuestionRepository;
import com.careerq.security.AuthUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class InteractionService {

    private static final Set<String> TARGET_TYPES = Set.of("answer", "question", "post");
    private static final Set<String> TYPES = Set.of("like", "favorite", "useful");

    private final InteractionRepository interactionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;

    public InteractionService(InteractionRepository interactionRepository,
                              AnswerRepository answerRepository,
                              QuestionRepository questionRepository) {
        this.interactionRepository = interactionRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
    }

    public record Request(@NotBlank String targetType, @NotNull Long targetId, @NotBlank String type) {
    }

    @Transactional
    public void interact(AuthUser user, Request request) {
        String targetType = request.targetType().trim().toLowerCase();
        String type = request.type().trim().toLowerCase();
        if (!TARGET_TYPES.contains(targetType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "targetType 不合法");
        }
        if (!TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "type 不合法");
        }
        if (interactionRepository.existsByUserIdAndTargetTypeAndTargetIdAndType(
                user.getId(), targetType, request.targetId(), type)) {
            return;
        }

        Interaction interaction = new Interaction();
        interaction.setUserId(user.getId());
        interaction.setTargetType(targetType);
        interaction.setTargetId(request.targetId());
        interaction.setType(type);
        interactionRepository.save(interaction);

        if ("answer".equals(targetType)) {
            Answer answer = answerRepository.findById(request.targetId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回答不存在"));
            if ("like".equals(type)) {
                answer.setLikeCount(answer.getLikeCount() + 1);
            } else if ("useful".equals(type)) {
                answer.setUsefulCount(answer.getUsefulCount() + 1);
            }
            answerRepository.save(answer);
        } else if ("question".equals(targetType) && "useful".equals(type)) {
            Question question = questionRepository.findById(request.targetId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
            question.setUsefulCount(question.getUsefulCount() + 1);
            questionRepository.save(question);
        }
    }
}
