package com.careerq.hr;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import java.util.List;

public final class HrNotificationDtos {
    private HrNotificationDtos() {}

    public record HrNotification(Long id, String type, String category, String title, String message,
                                 String questionTitle, Long questionId, Long answerId, Long followupId,
                                 String destination, LocalDateTime createdAt, LocalDateTime readAt) {}

    public record HrNotificationPage(List<HrNotification> items, Long nextCursor, long snapshotId) {}
    public record HrUnreadCounts(long total, long followup, long system, long snapshotId) {}
    public record ReadAllRequest(@NotNull @PositiveOrZero Long throughId) {}
}
