package com.careerq.answer;

import com.careerq.answer.AnswerDtos.AnswerView;
import com.careerq.answer.AnswerDtos.CreateRequest;
import com.careerq.common.ApiResponse;
import com.careerq.security.AuthUsers;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AnswerController {

    private final AnswerService answerService;

    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @PostMapping("/questions/{id}/answers")
    @PreAuthorize("hasRole('HR')")
    public ApiResponse<AnswerView> answer(Authentication authentication,
                                          @PathVariable Long id,
                                          @Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(answerService.create(AuthUsers.require(authentication), id, request));
    }
}
