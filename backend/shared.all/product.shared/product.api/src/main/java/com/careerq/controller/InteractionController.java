package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.security.AuthUsers;
import com.careerq.service.InteractionService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interactions")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    /** 点赞 / 有用 / 收藏（幂等）。 */
    @PostMapping
    public ApiResponse<Void> interact(Authentication authentication,
                                      @Valid @RequestBody InteractionService.Request request) {
        interactionService.interact(AuthUsers.require(authentication), request);
        return ApiResponse.ok();
    }

    /** 取消点赞 / 有用 / 收藏（幂等）。 */
    @DeleteMapping
    public ApiResponse<Void> uninteract(Authentication authentication,
                                        @Valid @RequestBody InteractionService.Request request) {
        interactionService.uninteract(AuthUsers.require(authentication), request);
        return ApiResponse.ok();
    }
}