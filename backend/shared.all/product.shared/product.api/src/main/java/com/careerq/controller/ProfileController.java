package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.common.PageResult;

import com.careerq.profile.ProfileDtos.ProfileView;
import com.careerq.profile.ProfileDtos.PatchRequest;
import com.careerq.profile.MeDtos.Stats;
import com.careerq.question.QuestionDtos.QuestionView;

import com.careerq.profile.ProfileDtos.UpdateRequest;
import com.careerq.security.AuthUsers;
import com.careerq.service.ProfileService;
import com.careerq.service.MeService;
import com.careerq.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心：可选完善资料（学历/经历）、头像、HR 公司资料。
 */
@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {

    private final ProfileService profileService;
    private final QuestionService questionService;
    private final MeService meService;

    public ProfileController(ProfileService profileService, QuestionService questionService, MeService meService) {
        this.profileService = profileService;
        this.questionService = questionService;
        this.meService = meService;
    }

    @GetMapping("/profile")
    public ApiResponse<ProfileView> get(Authentication authentication) {
        return ApiResponse.ok(profileService.get(AuthUsers.require(authentication)));
    }

    @PutMapping("/profile")
    public ApiResponse<ProfileView> update(Authentication authentication,
                                           @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(profileService.update(AuthUsers.require(authentication), request));
    }

    @PatchMapping("/profile")
    public ApiResponse<ProfileView> patch(Authentication authentication,
                                          @Valid @RequestBody PatchRequest request) {
        return ApiResponse.ok(profileService.patch(AuthUsers.require(authentication), request));
    }

    @GetMapping("/questions")
    public ApiResponse<PageResult<QuestionView>> questions(Authentication authentication,
                                                           @RequestParam(required = false) String scope,
                                                           @RequestParam(required = false) Long cursor,
                                                           @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(questionService.mine(AuthUsers.require(authentication), scope, cursor, limit));
    }

    @GetMapping("/stats")
    public ApiResponse<Stats> stats(Authentication authentication) {
        return ApiResponse.ok(meService.stats(AuthUsers.require(authentication)));
    }
}
