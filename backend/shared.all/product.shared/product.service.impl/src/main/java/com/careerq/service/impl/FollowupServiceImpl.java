package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.FollowupDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dataobject.FollowupDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.followup.FollowupDtos.CreateRequest;
import com.careerq.followup.FollowupDtos.FollowupView;
import com.careerq.security.AuthUser;
import com.careerq.service.FollowupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Service
public class FollowupServiceImpl implements FollowupService {

    private static final String PUBLISHED = "published";

    private final FollowupDao followupDao;
    private final QuestionDao questionDao;

    public FollowupServiceImpl(FollowupDao followupDao, QuestionDao questionDao) {
        this.followupDao = followupDao;
        this.questionDao = questionDao;
    }


    @Override
    @Transactional
    public FollowupView create(AuthUser sender, Long questionId, CreateRequest request) {
        if (!"student".equals(sender.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有学生可以追问");
        }
        QuestionDO question = questionDao.findByIdForUpdate(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        requireVisible(sender, question);

        FollowupDO followup = new FollowupDO();
        followup.setQuestionId(questionId);
        followup.setSenderId(sender.getId());
        followup.setSenderRole(sender.getRole());
        followup.setBody(request.body());
        followup.setScope("public");
        followup.setStatus(PUBLISHED);
        followupDao.insert(followup);
        return toView(followup);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowupView> list(AuthUser current, Long questionId) {
        QuestionDO question = questionDao.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        requireVisible(current, question);
        return followupDao.findVisible(questionId, null)
                .stream().map(this::toView).toList();
    }

    private void requireVisible(AuthUser current, QuestionDO question) {
        if (!"public".equals(question.getScope()) || !PUBLISHED.equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在");
        }
    }

    private FollowupView toView(FollowupDO followup) {
        return new FollowupView(
                followup.getId(),
                followup.getQuestionId(),
                followup.getSenderRole(),
                followup.getBody(),
                followup.getStatus(),
                followup.getCreatedAt());
    }
}
