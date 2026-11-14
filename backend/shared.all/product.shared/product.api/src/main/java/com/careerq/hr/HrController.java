package com.careerq.hr;

import com.careerq.common.ApiResponse;
import com.careerq.common.PageResult;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.question.QuestionService;
import com.careerq.security.AuthUsers;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/private-pool")
    @PreAuthorize("hasRole('HR')")
    public ApiResponse<PageResult<QuestionView>> privatePool(@RequestParam(required = false) Long cursor,
                                                             @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(questionService.hrPrivatePool(cursor, limit));
    }

    @PostMapping("/questions/{id}/claim")
    @PreAuthorize("hasRole('HR')")
    public ApiResponse<QuestionView> claim(Authentication authentication, @PathVariable Long id) {
        return ApiResponse.ok(questionService.claim(AuthUsers.require(authentication), id));
    }
}
