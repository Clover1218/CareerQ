package com.careerq.service.impl;

import com.careerq.dao.AnswerDao;
import com.careerq.dao.InteractionDao;
import com.careerq.dao.QuestionDao;
import com.careerq.profile.MeDtos.Stats;
import com.careerq.security.AuthUser;
import com.careerq.service.MeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeServiceImpl implements MeService {

    private final QuestionDao questionDao;
    private final AnswerDao answerDao;
    private final InteractionDao interactionDao;

    public MeServiceImpl(QuestionDao questionDao, AnswerDao answerDao, InteractionDao interactionDao) {
        this.questionDao = questionDao;
        this.answerDao = answerDao;
        this.interactionDao = interactionDao;
    }

    @Override
    @Transactional(readOnly = true)
    public Stats stats(AuthUser user) {
        Long userId = user.getId();
        return new Stats(questionDao.countByAuthor(userId), questionDao.countReceivedAnswers(userId),
                answerDao.countVisibleByAuthor(userId), interactionDao.countLikesReceived(userId));
    }
}
