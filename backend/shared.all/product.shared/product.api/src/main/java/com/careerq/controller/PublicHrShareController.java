package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.common.PageResult;
import com.careerq.hr.HrPostDtos.PublicShareDetail;
import com.careerq.hr.HrPostDtos.PublicShareSummary;
import com.careerq.service.HrPostService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hr-shares")
public class PublicHrShareController {
    private final HrPostService posts;

    public PublicHrShareController(HrPostService posts) {
        this.posts = posts;
    }

    @GetMapping
    public ApiResponse<PageResult<PublicShareSummary>> list(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(posts.listPublicShares(cursor, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<PublicShareDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(posts.getPublicShare(id));
    }
}
