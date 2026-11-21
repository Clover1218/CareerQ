package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.common.PageResult;
import com.careerq.hr.HrPersonalDtos.*;
import com.careerq.security.AuthUsers;
import com.careerq.service.HrPersonalService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hr")
@PreAuthorize("hasRole('HR')")
public class HrPersonalController {
    private final HrPersonalService personal;
    public HrPersonalController(HrPersonalService personal) { this.personal = personal; }

    @GetMapping("/profile")
    public ApiResponse<HrProfileView> profile(Authentication auth) {
        return ApiResponse.ok(personal.profile(AuthUsers.require(auth)));
    }

    @PatchMapping("/profile")
    public ApiResponse<HrProfileView> patch(Authentication auth, @Valid @RequestBody HrProfilePatch patch) {
        return ApiResponse.ok(personal.patchProfile(AuthUsers.require(auth), patch));
    }

    @GetMapping("/stats")
    public ApiResponse<HrContributionStats> stats(Authentication auth) {
        return ApiResponse.ok(personal.stats(AuthUsers.require(auth)));
    }

    @GetMapping("/answers")
    public ApiResponse<PageResult<HrAnswerRecord>> answers(Authentication auth,
            @RequestParam(defaultValue = "all") String kind, @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(personal.answers(AuthUsers.require(auth), kind, status, cursor, limit));
    }

    @GetMapping("/answers/{id}")
    public ApiResponse<HrAnswerRecord> answer(Authentication auth, @PathVariable Long id) {
        return ApiResponse.ok(personal.answer(AuthUsers.require(auth), id));
    }
}
