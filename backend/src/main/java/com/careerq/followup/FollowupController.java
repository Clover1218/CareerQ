package com.careerq.followup;

import com.careerq.common.ApiResponse;
import com.careerq.followup.FollowupService.CreateRequest;
import com.careerq.followup.FollowupService.FollowupView;
import com.careerq.security.AuthUsers;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/questions/{id}/followups")
public class FollowupController {

    private final FollowupService followupService;

    public FollowupController(FollowupService followupService) {
        this.followupService = followupService;
    }

    @PostMapping
    public ApiResponse<FollowupView> create(Authentication authentication,
                                            @PathVariable Long id,
                                            @Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(followupService.create(AuthUsers.require(authentication), id, request));
    }

    @GetMapping
    public ApiResponse<List<FollowupView>> list(Authentication authentication, @PathVariable Long id) {
        return ApiResponse.ok(followupService.list(AuthUsers.optional(authentication), id));
    }
}
