package com.careerq.student;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import java.util.List;

public final class StudentNotificationDtos {
    private StudentNotificationDtos() {}
    public record StudentNotification(Long id, String type, String category, String title, String message,
            String questionTitle, Long questionId, Long answerId, Long followupId, String destination,
            LocalDateTime createdAt, LocalDateTime readAt) {}
    public record StudentNotificationPage(List<StudentNotification> items, Long nextCursor, long snapshotId) {}
    public record StudentUnreadCounts(long total, long reply, long like, long system, long snapshotId) {}
    public record ReadAllRequest(@NotNull @PositiveOrZero Long throughId) {}
}
