package com.careerq.controller;

import com.careerq.service.QuestionService;

import com.careerq.common.ApiResponse;
import com.careerq.common.PageResult;
import com.careerq.question.QuestionDtos.CreateRequest;
import com.careerq.question.QuestionDtos.QuestionContentView;
import com.careerq.question.QuestionDtos.QuestionDetail;
import com.careerq.question.QuestionDtos.QuestionFeedItem;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.question.QuestionDtos.ThreadView;
import com.careerq.security.AuthUser;
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
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<QuestionView> create(Authentication authentication,
                                            @Valid @RequestBody CreateRequest request) {
        AuthUser user = AuthUsers.require(authentication);
        return ApiResponse.ok(questionService.create(user, request));
    }

    @GetMapping
    public ApiResponse<PageResult<QuestionFeedItem>> list(@RequestParam(required = false) String tag,
                                                          @RequestParam(required = false) Long cursor,
                                                          @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(questionService.list(tag, cursor, limit));
    }

    @GetMapping("/mine")
    public ApiResponse<PageResult<QuestionView>> mine(Authentication authentication,
                                                      @RequestParam(required = false) String scope,
                                                      @RequestParam(required = false) Long cursor,
                                                      @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(questionService.mine(AuthUsers.require(authentication), scope, cursor, limit));
    }
    @GetMapping("/{id}")
    public ApiResponse<QuestionDetail> detail(Authentication authentication, @PathVariable Long id) {
        return ApiResponse.ok(questionService.detail(AuthUsers.optional(authentication), id));
    }

    @GetMapping("/{id}/content")
    public ApiResponse<QuestionContentView> content(Authentication authentication, @PathVariable Long id) {
        return ApiResponse.ok(questionService.content(AuthUsers.optional(authentication), id));
    }

    @GetMapping("/{id}/thread")
    public ApiResponse<ThreadView> thread(Authentication authentication, @PathVariable Long id) {
        return ApiResponse.ok(questionService.thread(AuthUsers.optional(authentication), id));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<QuestionView> withdraw(Authentication authentication, @PathVariable Long id) {
        return ApiResponse.ok(questionService.withdraw(AuthUsers.require(authentication), id));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "已下线", description = "为兼容旧客户端保留；私密问答已下线，此接口始终返回 40001。")
    public ApiResponse<QuestionView> close(Authentication authentication, @PathVariable Long id) {
        AuthUsers.require(authentication);
        throw new BusinessException(ErrorCode.INVALID_PARAM, "私密问答已明确不做");
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "已下线", description = "为兼容旧客户端保留；私密问答已下线，此接口始终返回 40001。")
    public ApiResponse<QuestionView> publish(Authentication authentication, @PathVariable Long id) {
        AuthUsers.require(authentication);
        throw new BusinessException(ErrorCode.INVALID_PARAM, "私密问答已明确不做");
    }
}
