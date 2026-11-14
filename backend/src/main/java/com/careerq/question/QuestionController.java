package com.careerq.question;

import com.careerq.common.ApiResponse;
import com.careerq.common.PageResult;
import com.careerq.question.QuestionDtos.CreateRequest;
import com.careerq.question.QuestionDtos.QuestionDetail;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.security.AuthUser;
import com.careerq.security.AuthUsers;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    public ApiResponse<QuestionView> create(Authentication authentication,
                                            @Valid @RequestBody CreateRequest request) {
        AuthUser user = AuthUsers.require(authentication);
        return ApiResponse.ok(questionService.create(user, request));
    }

    @GetMapping
    public ApiResponse<PageResult<QuestionView>> list(@RequestParam(required = false) String tag,
                                                      @RequestParam(required = false) Long cursor,
                                                      @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(questionService.list(tag, cursor, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionDetail> detail(Authentication authentication, @PathVariable Long id) {
        return ApiResponse.ok(questionService.detail(AuthUsers.optional(authentication), id));
    }
}
