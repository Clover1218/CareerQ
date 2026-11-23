package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.security.AuthUsers;
import com.careerq.service.AdminService;
import com.careerq.service.AdminService.BatchDecideRequest;
import com.careerq.service.AdminService.ContextPage;
import com.careerq.service.AdminService.DecideRequest;
import com.careerq.service.AdminService.HandleReportRequest;
import com.careerq.service.AdminService.ReportView;
import com.careerq.service.AdminService.ReviewDetail;
import com.careerq.service.AdminService.ReviewPage;
import com.careerq.service.AdminService.UndoRequest;
import com.careerq.service.AdminService.WordRequest;
import com.careerq.service.AdminService.WordView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/moderation/queue")
    public ApiResponse<ReviewPage> queue(Authentication authentication,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String risk,
                                         @RequestParam(required = false) String arbitration,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.reviewQueue(
                AuthUsers.require(authentication), type, risk, arbitration, page, size));
    }

    @GetMapping("/moderation/reviewed")
    public ApiResponse<ReviewPage> reviewed(Authentication authentication,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String risk,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.reviewed(AuthUsers.require(authentication), type, status, risk, page, size));
    }

    @GetMapping("/moderation/{targetType}/{id}")
    public ApiResponse<ReviewDetail> detail(@PathVariable String targetType,
                                            @PathVariable Long id) {
        return ApiResponse.ok(adminService.detail(targetType, id));
    }

    @GetMapping("/moderation/{targetType}/{id}/context/older")
    public ApiResponse<ContextPage> contextOlder(@PathVariable String targetType,
                                                 @PathVariable Long id,
                                                 @RequestParam String cursor,
                                                 @RequestParam(defaultValue = "2") int limit) {
        return ApiResponse.ok(adminService.contextBefore(targetType, id, cursor, limit));
    }

    @GetMapping("/moderation/{targetType}/{id}/context/newer")
    public ApiResponse<ContextPage> contextNewer(@PathVariable String targetType,
                                                 @PathVariable Long id,
                                                 @RequestParam String cursor,
                                                 @RequestParam(defaultValue = "2") int limit) {
        return ApiResponse.ok(adminService.contextAfter(targetType, id, cursor, limit));
    }

    @PostMapping("/moderation/{targetType}/{id}/decide")
    public ApiResponse<Void> decide(Authentication authentication,
                                    @PathVariable String targetType,
                                    @PathVariable Long id,
                                    @Valid @RequestBody DecideRequest request) {
        adminService.decide(AuthUsers.require(authentication), targetType, id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/moderation/{targetType}/{id}/undo")
    public ApiResponse<Void> undo(Authentication authentication,
                                  @PathVariable String targetType,
                                  @PathVariable Long id,
                                  @RequestBody(required = false) UndoRequest request) {
        adminService.undo(AuthUsers.require(authentication), targetType, id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/moderation/batch-decide")
    public ApiResponse<Void> batchDecide(Authentication authentication,
                                         @Valid @RequestBody BatchDecideRequest request) {
        adminService.batchDecide(AuthUsers.require(authentication), request);
        return ApiResponse.ok();
    }

    @GetMapping("/moderation/words")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<WordView>> words() {
        return ApiResponse.ok(adminService.words());
    }

    @PostMapping("/moderation/words")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<WordView> upsertWord(@Valid @RequestBody WordRequest request) {
        return ApiResponse.ok(adminService.upsertWord(request));
    }

    @GetMapping("/reports")
    public ApiResponse<List<ReportView>> reports(@RequestParam(required = false) String status) {
        return ApiResponse.ok(adminService.reports(status));
    }

    @PostMapping("/reports/{id}/handle")
    public ApiResponse<Void> handleReport(Authentication authentication,
                                          @PathVariable Long id,
                                          @Valid @RequestBody HandleReportRequest request) {
        adminService.handleReport(AuthUsers.require(authentication), id, request.decision(), request.note());
        return ApiResponse.ok();
    }

    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> metrics() {
        return ApiResponse.ok(adminService.metrics());
    }
}