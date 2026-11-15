package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.security.AuthUsers;
import com.careerq.service.PublicPostService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PublicPostController {

    private final PublicPostService publicPostService;

    public PublicPostController(PublicPostService publicPostService) {
        this.publicPostService = publicPostService;
    }

    @GetMapping("/posts")
    public ApiResponse<List<PublicPostService.PostView>> list(Authentication authentication) {
        return ApiResponse.ok(publicPostService.list(AuthUsers.optional(authentication)));
    }

    @GetMapping("/feed/highlights")
    public ApiResponse<List<PublicPostService.PostView>> highlights(Authentication authentication) {
        return ApiResponse.ok(publicPostService.highlights(AuthUsers.optional(authentication)));
    }
}