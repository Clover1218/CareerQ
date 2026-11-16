package com.careerq.service.impl;

import com.careerq.dao.MeDao;
import com.careerq.dataobject.MeStatsDO;
import com.careerq.me.MeDtos.Stats;
import com.careerq.security.AuthUser;
import com.careerq.service.MeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeServiceImpl implements MeService {

    private final MeDao meDao;

    public MeServiceImpl(MeDao meDao) {
        this.meDao = meDao;
    }

    @Override
    @Transactional(readOnly = true)
    public Stats stats(AuthUser user) {
        MeStatsDO row = meDao.stats(user.getId());
        return new Stats(
                row.getQuestionCount(),
                row.getReceivedAnswerCount(),
                row.getAuthoredAnswerCount(),
                row.getQuestionLikeCount() + row.getAnswerLikeCount());
    }
}
