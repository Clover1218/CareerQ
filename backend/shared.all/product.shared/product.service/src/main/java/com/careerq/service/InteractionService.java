package com.careerq.service;

import com.careerq.security.AuthUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public interface InteractionService {

    void interact(AuthUser user, Request request);

    record Request(@NotBlank String targetType, @NotNull Long targetId, @NotBlank String type) {
    }
}
