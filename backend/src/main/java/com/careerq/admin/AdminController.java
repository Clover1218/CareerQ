package com.careerq.admin;

import com.careerq.admin.AdminService.DecideRequest;
import com.careerq.admin.AdminService.HandleReportRequest;
import com.careerq.admin.AdminService.QueueItem;
import com.careerq.admin.AdminService.WordRequest;
import com.careerq.common.ApiResponse;
import com.careerq.moderation.ModerationWord;
import com.careerq.report.Report;
import com.careerq.security.AuthUsers;
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
    public ApiResponse<List<QueueItem>> queue() {
        return ApiResponse.ok(adminService.queue());
    }

    @PostMapping("/moderation/{targetType}/{id}/decide")
    public ApiResponse<Void> decide(Authentication authentication,
                                    @PathVariable String targetType,
                                    @PathVariable Long id,
                                    @Valid @RequestBody DecideRequest request) {
        adminService.decide(AuthUsers.require(authentication), targetType, id, request.decision(), request.note());
        return ApiResponse.ok();
    }

    @GetMapping("/moderation/words")
    public ApiResponse<List<ModerationWord>> words() {
        return ApiResponse.ok(adminService.words());
    }

    @PostMapping("/moderation/words")
    public ApiResponse<ModerationWord> upsertWord(@Valid @RequestBody WordRequest request) {
        return ApiResponse.ok(adminService.upsertWord(request));
    }

    @GetMapping("/reports")
    public ApiResponse<List<Report>> reports(@RequestParam(required = false) String status) {
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
