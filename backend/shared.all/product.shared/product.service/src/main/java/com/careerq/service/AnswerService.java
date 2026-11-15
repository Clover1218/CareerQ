package com.careerq.service;

import com.careerq.answer.AnswerDtos.AnswerView;
import com.careerq.answer.AnswerDtos.CreateRequest;
import com.careerq.security.AuthUser;

public interface AnswerService {

    AnswerView create(AuthUser hr, Long questionId, CreateRequest request);
}
