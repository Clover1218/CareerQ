package com.careerq.followup;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.question.Question;
import com.careerq.question.QuestionRepository;
import com.careerq.security.AuthUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FollowupService {

    private static final String PUBLISHED = "published";

    private final FollowupRepository followupRepository;
    private final QuestionRepository questionRepository;

    public FollowupService(FollowupRepository followupRepository, QuestionRepository questionRepository) {
        this.followupRepository = followupRepository;
        this.questionRepository = questionRepository;
    }

    public record CreateRequest(
            @NotBlank(message = "追问问内容不能为空")
            @Size(max = 2000, message = "追问不能超过 2000 字")
            String body) {
    }

    public record FollowupView(Long id, Long questionId, String senderRole, String body, LocalDateTime createdAt) {
    }

    @Transactional
    public FollowupView create(AuthUser sender, Long questionId, CreateRequest request) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        requireVisible(sender, question);

        Followup followup = new Followup();
        followup.setQuestionId(questionId);
        followup.setSenderId(sender.getId());
        followup.setSenderRole(sender.getRole());
        followup.setBody(request.body());
        followup.setScope(question.getScope());
        followup.setStatus(PUBLISHED);
        followupRepository.save(followup);
        return toView(followup);
    }

    @Transactional(readOnly = true)
    public List<FollowupView> list(AuthUser current, Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        requireVisible(current, question);
        return followupRepository.findByQuestionIdAndStatusOrderByIdAsc(questionId, PUBLISHED)
                .stream().map(this::toView).toList();
    }

    private void requireVisible(AuthUser current, Question question) {
        if ("public".equals(question.getScope())) {
            // 公开问题的追问可匿名浏览；写入路径由控制器要求登录
            return;
        }
        if (current == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该私密问题");
        }
        boolean isAuthor = current.getId().equals(question.getAuthorId());
        boolean isAssignedHr = current.getId().equals(question.getAssignedHrId());
        boolean isOperator = "operator".equals(current.getRole()) || "admin".equals(current.getRole());
        if (!isAuthor && !isAssignedHr && !isOperator) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该私密问题");
        }
    }

    private FollowupView toView(Followup followup) {
        return new FollowupView(
                followup.getId(),
                followup.getQuestionId(),
                followup.getSenderRole(),
                followup.getBody(),
                followup.getCreatedAt());
    }
}
