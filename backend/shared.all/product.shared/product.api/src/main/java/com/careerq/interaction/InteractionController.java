package com.careerq.interaction;

import com.careerq.common.ApiResponse;
import com.careerq.security.AuthUsers;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
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

    @PostMapping
    public ApiResponse<Void> interact(Authentication authentication,
                                      @Valid @RequestBody InteractionService.Request request) {
        interactionService.interact(AuthUsers.require(authentication), request);
        return ApiResponse.ok();
    }
}
