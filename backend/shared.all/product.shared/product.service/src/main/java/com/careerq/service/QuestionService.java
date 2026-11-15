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

    PageResult<QuestionView> hrPrivatePool(Long cursor, int limit);

    QuestionView claim(AuthUser hr, Long id);

    QuestionView close(AuthUser author, Long id);

    QuestionView publish(AuthUser author, Long id);
}
