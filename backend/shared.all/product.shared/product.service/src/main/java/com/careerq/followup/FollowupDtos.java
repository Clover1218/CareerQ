package com.careerq.followup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class FollowupDtos {

    private FollowupDtos() {
    }

    public record CreateRequest(
            @NotBlank(message = "追问问内容不能为空")
            @Size(max = 2000, message = "追问不能超过 2000 字")
            String body) {
    }

    public record FollowupView(Long id, Long questionId, String senderRole, String body, LocalDateTime createdAt) {
    }
}