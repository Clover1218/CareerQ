package com.careerq.controller;

import com.careerq.common.ApiResponse;
import com.careerq.common.PageResult;
import com.careerq.me.MeDtos.Stats;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.security.AuthUsers;
import com.careerq.service.MeService;
import com.careerq.service.QuestionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 个人主页：我的提问列表与获赞/回答统计。 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final MeService meService;
    private final QuestionService questionService;

    public MeController(MeService meService, QuestionService questionService) {
        this.meService = meService;
        this.questionService = questionService;
    }

    @GetMapping("/stats")
    public ApiResponse<Stats> stats(Authentication authentication) {
        return ApiResponse.ok(meService.stats(AuthUsers.require(authentication)));
    }

    @GetMapping("/questions")
    public ApiResponse<PageResult<QuestionView>> questions(Authentication authentication,
                                                          @RequestParam(required = false) String scope,
                                                          @RequestParam(required = false) Long cursor,
                                                          @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(questionService.mine(
                AuthUsers.require(authentication), scope, cursor, limit));
    }
}
