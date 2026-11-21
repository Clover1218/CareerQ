package com.careerq.answer;

import com.careerq.common.UserBrief;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AnswerDtos {

    private AnswerDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "回答内容不能为空")
            @Size(max = 5000, message = "回答不能超过 5000 字")
            String body,
            String lengthType) {
    }

    public record AnswerView(
            Long id,
            Long questionId,
            Long followupId,
            String body,
            String lengthType,
            String status,
            /** removed 时为 author / operator / unknown，其他状态为空。 */
            String removalSource,
            UserBrief author,
            int likeCount,
            int usefulCount,
            boolean liked,
            boolean usefuled,
            LocalDateTime createdAt) {
    }
}
