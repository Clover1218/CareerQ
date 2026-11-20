package com.careerq.controller;

import com.careerq.service.QuestionService;

import com.careerq.common.ApiResponse;
import com.careerq.common.PageResult;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.question.QuestionDtos.HrAnsweredQuestionView;
import com.careerq.question.QuestionDtos.HrAnsweredPublicQuestionView;

import com.careerq.security.AuthUsers;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/hr")
public class HrController {

    private final QuestionService questionService;

    public HrController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping("/question-pool")
    @PreAuthorize("hasRole('HR')")
    public ApiResponse<PageResult<QuestionView>> questionPool(@RequestParam(required = false) Long cursor,
                                                              @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(questionService.hrQuestionPool(cursor, limit));
    }

    @GetMapping("/answered-public-questions")
    @PreAuthorize("hasRole('HR')")
    public ApiResponse<PageResult<HrAnsweredPublicQuestionView>> answeredPublicQuestions(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(questionService.hrAnsweredPublicQuestions(cursor, limit));
    }

    @GetMapping("/answered-questions")
    @PreAuthorize("hasRole('HR')")
    public ApiResponse<PageResult<HrAnsweredQuestionView>> answeredQuestions(
            Authentication authentication,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(questionService.hrAnsweredQuestions(AuthUsers.require(authentication), cursor, limit));
    }

    @GetMapping("/private-pool")
    @PreAuthorize("hasRole('HR')")
    @Operation(summary = "已下线", description = "为兼容旧客户端保留；私密问答已下线，此接口始终返回 40001。")
    public ApiResponse<PageResult<QuestionView>> privatePool(@RequestParam(required = false) Long cursor,
                                                             @RequestParam(defaultValue = "20") int limit) {
        throw new BusinessException(ErrorCode.INVALID_PARAM, "私密问答已明确不做");
    }

    @PostMapping("/questions/{id}/claim")
    @PreAuthorize("hasRole('HR')")
    @Operation(summary = "已下线", description = "为兼容旧客户端保留；私密问答已下线，此接口始终返回 40001。")
    public ApiResponse<QuestionView> claim(Authentication authentication, @PathVariable Long id) {
        AuthUsers.require(authentication);
        throw new BusinessException(ErrorCode.INVALID_PARAM, "私密问答已明确不做");
    }
}
