package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.student.StudentNotificationDtos.*;
import com.careerq.security.AuthUsers;
import com.careerq.service.StudentNotificationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student/notifications")
@PreAuthorize("hasRole('STUDENT')")
public class StudentNotificationController {
    private final StudentNotificationService notifications;
    public StudentNotificationController(StudentNotificationService notifications) { this.notifications = notifications; }

    @GetMapping
    public ApiResponse<StudentNotificationPage> list(Authentication auth, @RequestParam(defaultValue = "all") String category,
            @RequestParam(defaultValue = "false") boolean unreadOnly, @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(notifications.list(AuthUsers.require(auth), category, unreadOnly, cursor, limit));
    }

    @GetMapping("/unread-count")
    public ApiResponse<StudentUnreadCounts> unread(Authentication auth) {
        return ApiResponse.ok(notifications.unread(AuthUsers.require(auth)));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<StudentNotification> read(Authentication auth, @PathVariable Long id) {
        return ApiResponse.ok(notifications.read(AuthUsers.require(auth), id));
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll(Authentication auth, @Valid @RequestBody ReadAllRequest request) {
        notifications.readAll(AuthUsers.require(auth), request.throughId());
        return ApiResponse.ok();
    }
}
