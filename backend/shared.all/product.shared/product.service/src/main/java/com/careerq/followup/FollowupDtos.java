package com.careerq.followup;

import com.careerq.common.UserBrief;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class FollowupDtos {

    private FollowupDtos() {
    }

    public record CreateRequest(
            @NotNull(message = "追问必须指定目标 HR")
            Long hrId,
            @NotBlank(message = "追问问内容不能为空")
            @Size(max = 2000, message = "追问不能超过 2000 字")
            String body) {
    }

    public record FollowupView(Long id, Long questionId, Long hrId, String senderRole, String body,
                               String displayStatus, LocalDateTime createdAt, UserBrief author, boolean hr) {
    }
}
