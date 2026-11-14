package com.careerq.hr;

import com.careerq.common.ApiResponse;
import com.careerq.common.PageResult;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.question.QuestionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
}
