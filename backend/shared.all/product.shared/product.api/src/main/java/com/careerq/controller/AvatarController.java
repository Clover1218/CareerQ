package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.profile.ProfileDtos.ProfileView;
import com.careerq.security.AuthUser;
import com.careerq.security.AuthUsers;
import com.careerq.service.AvatarService;
import com.careerq.service.AvatarService.AvatarFile;
import com.careerq.service.ProfileService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * 头像：仅支持上传图片（服务端存储），不接受外部 URL。
 */
@RestController
@RequestMapping("/api/v1")
public class AvatarController {

    private final AvatarService avatarService;
    private final ProfileService profileService;

    public AvatarController(AvatarService avatarService, ProfileService profileService) {
        this.avatarService = avatarService;
        this.profileService = profileService;
    }

    @PostMapping("/me/avatar")
    public ApiResponse<ProfileView> upload(Authentication authentication,
                                           @RequestParam("file") MultipartFile file) {
        AuthUser user = AuthUsers.require(authentication);
        avatarService.store(user, file);
        return ApiResponse.ok(profileService.get(user));
    }

    @GetMapping("/avatars/{filename}")
    public ResponseEntity<byte[]> read(@PathVariable String filename) {
        AvatarFile file = avatarService.load(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.data());
    }
}