package com.careerq.controller;

import com.careerq.common.ApiResponse;

import com.careerq.profile.ProfileDtos.ProfileView;
import com.careerq.profile.ProfileDtos.UpdateAvatarRequest;
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
 * 个人中心：可选完善资料（学历/经历）、头像、HR 公司资料。
 */
@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
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

    /** 更新头像地址（传空字符串表示清空）。 */
    @PutMapping("/avatar")
    public ApiResponse<ProfileView> updateAvatar(Authentication authentication,
                                                 @Valid @RequestBody UpdateAvatarRequest request) {
        return ApiResponse.ok(profileService.updateAvatar(
                AuthUsers.require(authentication), request.avatarUrl()));
    }
}
