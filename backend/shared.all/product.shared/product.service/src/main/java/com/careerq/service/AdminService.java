package com.careerq.service;

import com.careerq.security.AuthUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AdminService {

    ReviewPage reviewQueue(AuthUser operator, String type, String risk, String arbitration,
                           int page, int size);

    ReviewPage reviewed(AuthUser operator, String type, String status, String risk, int page, int size);

    ReviewDetail detail(String targetType, Long targetId);

    ContextPage contextBefore(String targetType, Long targetId, String cursor, int limit);

    ContextPage contextAfter(String targetType, Long targetId, String cursor, int limit);

    void decide(AuthUser operator, String targetType, Long targetId, DecideRequest request);

    void undo(AuthUser operator, String targetType, Long targetId, UndoRequest request);

    void batchDecide(AuthUser operator, BatchDecideRequest request);

    List<ReportView> reports(String status);

    void handleReport(AuthUser operator, Long reportId, String decision, String note);

    List<WordView> words();

    WordView upsertWord(WordRequest request);

    Map<String, Object> metrics();

    record ReviewPage(List<ReviewItem> items, long total, int page, int size) {
    }

    record ReviewItem(String targetType, Long targetId, String title, String snippet, String body,
                      String riskLevel, String displayStatus, Long authorId, String authorNickname,
                      Long questionId, String questionTitle, LocalDateTime createdAt,
                      LocalDateTime reviewedAt, String reviewDecision,
                      int reviewAttempts, boolean arbitrationRequired, boolean canUndo) {
    }

    record ReviewDetail(ReviewItem item, List<ReviewEntry> context,
                        int contextBeforeRemaining, int contextAfterRemaining,
                        List<RecordView> records) {
    }

    record ReviewEntry(String kind, Long id, String authorNickname, String senderRole, String body,
                       String displayStatus, boolean target, LocalDateTime createdAt) {
    }

    record ContextPage(List<ReviewEntry> entries, int remaining) {
    }

    record RecordView(Long id, String stage, String decision, String riskLevel, String riskLabels,
                      Double confidence, String note, String operatorNickname,
                      String fromStatus, String toStatus, LocalDateTime createdAt) {
    }

    record DecideRequest(@NotBlank String decision, @Size(max = 500) String note) {
    }

    record UndoRequest(@Size(max = 500) String note) {
    }

    record BatchDecideRequest(List<TargetRef> targets, @NotBlank String decision,
                              @Size(max = 500) String note) {
    }

    record TargetRef(String targetType, Long targetId) {
    }

    record HandleReportRequest(@NotBlank String decision, String note) {
    }

    record WordRequest(@NotBlank String word, String category, String action) {
    }

    record ReportView(Long id, Long reporterId, String targetType, Long targetId,
                      String reason, String detail, String status,
                      Long handledBy, LocalDateTime handledAt, LocalDateTime createdAt,
                      String targetTitle, String targetExcerpt) {
    }

    record WordView(Long id, String word, String category, String action, LocalDateTime updatedAt) {
    }
}