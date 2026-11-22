package com.careerq.followup;

import com.careerq.common.UserBrief;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class FollowupDtos {

    private FollowupDtos() {
    }

    public record CreateRequest(
            @NotNull(message = "追问必须指定目标 HR")
            Long hrId,
            @NotBlank(message = "追问内容不能为空")
            @Size(max = 2000, message = "追问不能超过 2000 字")
            String body,
            @NotNull(message = "追问必须指定具体回答")
            @Positive(message = "目标回答无效")
            Long rootAnswerId) {
    }

public record FollowupView(Long id, Long questionId, Long hrId, Long rootAnswerId, String senderRole, String body,
                               String displayStatus, String status,
                               LocalDateTime createdAt, UserBrief author, boolean hr) {
    }
}
