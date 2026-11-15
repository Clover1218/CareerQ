package com.careerq.service;

import com.careerq.security.AuthUser;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AdminService {

    List<QueueItem> queue();

    void decide(AuthUser operator, String targetType, Long targetId, String decision, String note);

    List<ReportView> reports(String status);

    void handleReport(AuthUser operator, Long reportId, String decision, String note);

    List<WordView> words();

    WordView upsertWord(WordRequest request);

    Map<String, Object> metrics();

    record QueueItem(String targetType, Long targetId, String title, String snippet,
                     String status, LocalDateTime createdAt) {
    }

    record DecideRequest(@NotBlank String decision, String note) {
    }

    record HandleReportRequest(@NotBlank String decision, String note) {
    }

    record WordRequest(@NotBlank String word, String category, String action) {
    }

    record ReportView(Long id, Long reporterId, String targetType, Long targetId,
                      String reason, String detail, String status,
                      Long handledBy, LocalDateTime handledAt, LocalDateTime createdAt) {
    }

    record WordView(Long id, String word, String category, String action, LocalDateTime updatedAt) {
    }
}
