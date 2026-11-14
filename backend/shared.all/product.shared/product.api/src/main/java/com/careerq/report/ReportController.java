package com.careerq.report;

import com.careerq.common.ApiResponse;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.security.AuthUser;
import com.careerq.security.AuthUsers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final Set<String> TARGET_TYPES = Set.of("question", "answer", "post", "public_post");

    private final ReportRepository reportRepository;

    public ReportController(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    public record CreateRequest(
            @NotBlank String targetType,
            @NotNull Long targetId,
            @NotBlank @Size(max = 32) String reason,
            @Size(max = 500) String detail) {
    }

    @PostMapping
    public ApiResponse<Void> create(Authentication authentication, @RequestBody CreateRequest request) {
        AuthUser user = AuthUsers.require(authentication);
        String targetType = request.targetType().trim().toLowerCase();
        if (!TARGET_TYPES.contains(targetType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "targetType 不合法");
        }
        Report report = new Report();
        report.setReporterId(user.getId());
        report.setTargetType(targetType);
        report.setTargetId(request.targetId());
        report.setReason(request.reason());
        report.setDetail(request.detail());
        report.setStatus("pending");
        reportRepository.save(report);
        return ApiResponse.ok();
    }
}
