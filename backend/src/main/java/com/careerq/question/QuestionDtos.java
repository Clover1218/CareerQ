package com.careerq.question;

import com.careerq.answer.AnswerDtos.AnswerView;
import com.careerq.common.UserBrief;
import com.careerq.followup.FollowupService.FollowupView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class QuestionDtos {

    private QuestionDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "标题不能为空")
            @Size(max = 200, message = "标题不能超过 200 字")
            String title,
            @NotBlank(message = "问题描述不能为空")
            @Size(max = 5000, message = "描述不能超过 5000 字")
            String body,
            List<String> tags,
            String scope) {
    }

    public record QuestionView(
            Long id,
            String scope,
            String status,
            String title,
            String body,
            List<String> tags,
            UserBrief author,
            LocalDateTime createdAt,
            int answerCount,
            int usefulCount,
            LocalDateTime firstAnswerAt) {
    }

    public record QuestionDetail(QuestionView question, List<AnswerView> answers, List<FollowupView> followups) {
    }
}
