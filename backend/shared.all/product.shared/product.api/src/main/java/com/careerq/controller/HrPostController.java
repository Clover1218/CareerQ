package com.careerq.controller;

import com.careerq.common.*;
import com.careerq.hr.HrPostDtos.*;
import com.careerq.security.*;
import com.careerq.service.HrPostService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hr/posts")
@PreAuthorize("hasRole('HR')")
public class HrPostController {
    private final HrPostService posts;
    public HrPostController(HrPostService posts) { this.posts = posts; }
    private AuthUser current(Authentication auth, Long expectedHr) {
        AuthUser user = AuthUsers.require(auth);
        if (expectedHr != null && !expectedHr.equals(user.getId())) throw new BusinessException(ErrorCode.SESSION_CHANGED);
        return user;
    }
    @GetMapping
    public ApiResponse<PageResult<HrPostSummary>> list(Authentication auth, @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "5") int limit,
            @RequestHeader(value = "X-CareerQ-Hr-Id", required = false) Long expectedHr) {
        return ApiResponse.ok(posts.list(current(auth, expectedHr), status, cursor, limit));
    }
    @GetMapping("/{id}")
    public ApiResponse<HrPostView> get(Authentication auth, @PathVariable Long id,
            @RequestHeader(value = "X-CareerQ-Hr-Id", required = false) Long expectedHr) {
        return ApiResponse.ok(posts.get(current(auth, expectedHr), id));
    }
    @PostMapping("/drafts")
    public ApiResponse<HrPostView> create(Authentication auth, @Valid @RequestBody CreateDraftRequest request,
            @RequestHeader(value = "X-CareerQ-Hr-Id", required = false) Long expectedHr) {
        return ApiResponse.ok(posts.createDraft(current(auth, expectedHr), request));
    }
    @PutMapping("/{id}/draft")
    public ApiResponse<HrPostView> save(Authentication auth, @PathVariable Long id, @Valid @RequestBody SaveDraftRequest request,
            @RequestHeader(value = "X-CareerQ-Hr-Id", required = false) Long expectedHr) {
        return ApiResponse.ok(posts.saveDraft(current(auth, expectedHr), id, request));
    }
    @PostMapping("/{id}/submit")
    public ApiResponse<HrPostView> submit(Authentication auth, @PathVariable Long id, @Valid @RequestBody VersionRequest request,
            @RequestHeader(value = "X-CareerQ-Hr-Id", required = false) Long expectedHr) {
        return ApiResponse.ok(posts.submit(current(auth, expectedHr), id, request.version()));
    }
    @PostMapping("/{id}/withdraw")
    public ApiResponse<HrPostView> withdraw(Authentication auth, @PathVariable Long id, @Valid @RequestBody VersionRequest request,
            @RequestHeader(value = "X-CareerQ-Hr-Id", required = false) Long expectedHr) {
        return ApiResponse.ok(posts.withdraw(current(auth, expectedHr), id, request.version()));
    }
    @DeleteMapping("/{id}/draft")
    public ApiResponse<Void> delete(Authentication auth, @PathVariable Long id, @Valid @RequestBody VersionRequest request,
            @RequestHeader(value = "X-CareerQ-Hr-Id", required = false) Long expectedHr) {
        posts.deleteDraft(current(auth, expectedHr), id, request.version()); return ApiResponse.ok();
    }
}
