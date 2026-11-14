package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.security.AuthUsers;
import com.careerq.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ApiResponse<Void> create(Authentication authentication,
                                    @Valid @RequestBody ReportService.CreateRequest request) {
        reportService.create(AuthUsers.require(authentication), request);
        return ApiResponse.ok();
    }
}
