package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.profile.ProfileDtos.ProfileView;
import com.careerq.profile.ProfileDtos.UpdateRequest;
import com.careerq.security.AuthUsers;
import com.careerq.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 可选完善资料：学历信息 + 实习/项目经历。
 */
@RestController
@RequestMapping("/api/v1/me/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<ProfileView> get(Authentication authentication) {
        return ApiResponse.ok(profileService.get(AuthUsers.require(authentication)));
    }

    @PutMapping
    public ApiResponse<ProfileView> update(Authentication authentication,
                                           @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(profileService.update(AuthUsers.require(authentication), request));
    }
}
