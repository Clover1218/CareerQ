package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.question.QuestionDtos.FeedPage;
import com.careerq.security.AuthUsers;
import com.careerq.service.QuestionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

    private final QuestionService questionService;

    public FeedController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /** 推荐流：可选登录，游客走「质量 + 新鲜度」冷启动排序。 */
    @GetMapping("/questions")
    public ApiResponse<FeedPage> questions(Authentication authentication,
                                           @RequestParam(required = false) String tag,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(questionService.feed(AuthUsers.optional(authentication), tag, cursor, limit));
    }
}
