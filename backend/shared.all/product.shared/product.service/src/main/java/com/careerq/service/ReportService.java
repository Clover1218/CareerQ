package com.careerq.service;

import com.careerq.security.AuthUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public interface ReportService {

    void create(AuthUser reporter, CreateRequest request);

    record CreateRequest(
            @NotBlank String targetType,
            @NotNull Long targetId,
            @NotBlank @Size(max = 32) String reason,
            @Size(max = 500) String detail) {
    }
}
