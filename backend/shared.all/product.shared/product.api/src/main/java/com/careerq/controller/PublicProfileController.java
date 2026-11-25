package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.common.PageResult;
import com.careerq.hr.HrPostDtos.PublicShareSummary;
import com.careerq.profile.PublicProfileDtos.*;
import com.careerq.service.PublicProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/{id}")
public class PublicProfileController {
    private final PublicProfileService profiles;

    public PublicProfileController(PublicProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/profile")
    public ApiResponse<Profile> profile(@PathVariable Long id) {
        return ApiResponse.ok(profiles.profile(id));
    }

    @GetMapping("/questions")
    public ApiResponse<PageResult<QuestionEntry>> questions(@PathVariable Long id,
            @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(profiles.questions(id, cursor, limit));
    }

    @GetMapping("/answers")
    public ApiResponse<PageResult<AnswerEntry>> answers(@PathVariable Long id,
            @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(profiles.answers(id, cursor, limit));
    }

    @GetMapping("/hr-shares")
    public ApiResponse<PageResult<PublicShareSummary>> shares(@PathVariable Long id,
            @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(profiles.shares(id, cursor, limit));
    }
}
