package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.hr.HrNotificationDtos.*;
import com.careerq.security.AuthUsers;
import com.careerq.service.HrNotificationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hr/notifications")
@PreAuthorize("hasRole('HR')")
public class HrNotificationController {
    private final HrNotificationService notifications;
    public HrNotificationController(HrNotificationService notifications) { this.notifications = notifications; }

    @GetMapping
    public ApiResponse<HrNotificationPage> list(Authentication auth, @RequestParam(defaultValue = "all") String category,
            @RequestParam(defaultValue = "false") boolean unreadOnly, @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(notifications.list(AuthUsers.require(auth), category, unreadOnly, cursor, limit));
    }

    @GetMapping("/unread-count")
    public ApiResponse<HrUnreadCounts> unread(Authentication auth) {
        return ApiResponse.ok(notifications.unread(AuthUsers.require(auth)));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<HrNotification> read(Authentication auth, @PathVariable Long id) {
        return ApiResponse.ok(notifications.read(AuthUsers.require(auth), id));
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll(Authentication auth, @Valid @RequestBody ReadAllRequest request) {
        notifications.readAll(AuthUsers.require(auth), request.throughId());
        return ApiResponse.ok();
    }
}
