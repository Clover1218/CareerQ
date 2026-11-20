package com.careerq.service;

import com.careerq.common.PageResult;
import com.careerq.question.QuestionDtos.CreateRequest;
import com.careerq.question.QuestionDtos.FeedPage;
import com.careerq.question.QuestionDtos.FeedQuery;
import com.careerq.question.QuestionDtos.HrAnsweredQuestionView;
import com.careerq.question.QuestionDtos.HrAnsweredPublicQuestionView;
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

    FeedPage feed(AuthUser viewer, FeedQuery query, String cursor, int limit);

    PageResult<QuestionView> mine(AuthUser author, String scope, Long cursor, int limit);

    QuestionDetail detail(AuthUser current, Long id);

    QuestionContentView content(AuthUser current, Long id);

    ThreadView thread(AuthUser current, Long id);


    PageResult<QuestionView> hrQuestionPool(Long cursor, int limit);

    PageResult<HrAnsweredPublicQuestionView> hrAnsweredPublicQuestions(Long cursor, int limit);

    PageResult<HrAnsweredQuestionView> hrAnsweredQuestions(AuthUser hr, Long cursor, int limit);

    /** 提问者撤回自己的问题（仅未产生回答且未结单时可用）。 */
    QuestionView withdraw(AuthUser author, Long id);
}
