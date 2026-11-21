package com.careerq.controller;

import com.careerq.service.QuestionService;

import com.careerq.common.ApiResponse;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.PageResult;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.question.QuestionDtos.HrAnsweredQuestionPage;
import com.careerq.question.QuestionDtos.HrAnsweredPublicQuestionView;

import com.careerq.security.AuthUsers;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

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
    public ApiResponse<HrAnsweredQuestionPage> answeredQuestions(
            Authentication authentication,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) String cursorAt,
            @RequestParam(defaultValue = "false") boolean pendingFollowupsOnly,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(questionService.hrAnsweredQuestions(
                AuthUsers.require(authentication), cursor, parseCursorAt(cursorAt), limit, pendingFollowupsOnly));
    }

    private LocalDateTime parseCursorAt(String cursorAt) {
        if (cursorAt == null) return null;
        try {
            return LocalDateTime.parse(cursorAt);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "翻页参数无效，请返回第一页重试");
        }
    }
}
