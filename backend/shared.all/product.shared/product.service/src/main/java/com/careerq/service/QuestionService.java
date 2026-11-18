package com.careerq.service;

import com.careerq.common.PageResult;
import com.careerq.question.QuestionDtos.CreateRequest;
import com.careerq.question.QuestionDtos.FeedPage;
import com.careerq.question.QuestionDtos.QuestionContentView;
import com.careerq.question.QuestionDtos.QuestionDetail;
import com.careerq.question.QuestionDtos.QuestionFeedItem;
import com.careerq.question.QuestionDtos.QuestionView;
import com.careerq.question.QuestionDtos.ThreadView;
import com.careerq.security.AuthUser;

public interface QuestionService {

    QuestionView create(AuthUser author, CreateRequest request);

    PageResult<QuestionFeedItem> list(String tag, Long cursor, int limit);

    FeedPage feed(AuthUser viewer, String tag, String cursor, int limit);

    PageResult<QuestionView> mine(AuthUser author, String scope, Long cursor, int limit);

    QuestionDetail detail(AuthUser current, Long id);

    QuestionContentView content(AuthUser current, Long id);

    ThreadView thread(AuthUser current, Long id);


    PageResult<QuestionView> hrQuestionPool(Long cursor, int limit);

    PageResult<QuestionView> hrPrivatePool(Long cursor, int limit);

    QuestionView claim(AuthUser hr, Long id);

    QuestionView close(AuthUser author, Long id);

    QuestionView publish(AuthUser author, Long id);
}
