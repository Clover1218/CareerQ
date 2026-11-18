package com.careerq.service;

import com.careerq.common.PageResult;
import com.careerq.question.QuestionDtos.CreateRequest;
import com.careerq.question.QuestionDtos.QuestionDetail;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.security.AuthUser;

public interface QuestionService {

    QuestionView create(AuthUser author, CreateRequest request);

    PageResult<QuestionView> list(String tag, Long cursor, int limit);

    PageResult<QuestionView> mine(AuthUser author, String scope, Long cursor, int limit);

    QuestionDetail detail(AuthUser current, Long id);


    PageResult<QuestionView> hrQuestionPool(Long cursor, int limit);

    PageResult<QuestionView> hrAnsweredQuestions(AuthUser hr, Long cursor, int limit);
}
