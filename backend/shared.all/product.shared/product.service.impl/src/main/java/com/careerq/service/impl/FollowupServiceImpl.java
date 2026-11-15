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
import com.careerq.service.ModerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class FollowupServiceImpl implements FollowupService {

    private static final String PUBLISHED = "published";
    private static final Set<String> HR_VISIBLE_STATES =
            Set.of("assigned", "closed", "public_pending", "public_archived", "public_rejected");

    private final FollowupDao followupDao;
    private final QuestionDao questionDao;
    private final ModerationService moderationService;

    public FollowupServiceImpl(FollowupDao followupDao, QuestionDao questionDao,
                               ModerationService moderationService) {
        this.followupDao = followupDao;
        this.questionDao = questionDao;
        this.moderationService = moderationService;
    }


    @Override
    @Transactional
    public FollowupView create(AuthUser sender, Long questionId, CreateRequest request) {
        QuestionDO question = questionDao.findByIdForUpdate(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        requireVisible(sender, question);
        boolean privateQuestion = "private".equals(question.getScope());
        if (privateQuestion && (!"assigned".equals(question.getStatus())
                || !(sender.getId().equals(question.getAuthorId()) || sender.getId().equals(question.getAssignedHrId())))) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已认领且未结单的私密工单允许双方追问");
        }

        FollowupDO followup = new FollowupDO();
        followup.setQuestionId(questionId);
        followup.setSenderId(sender.getId());
        followup.setSenderRole(sender.getRole());
        followup.setBody(request.body());
        followup.setScope(question.getScope());
        followup.setStatus(privateQuestion ? "pending_human" : PUBLISHED);
        followupDao.insert(followup);
        if (privateQuestion) {
            moderationService.recordMachine("followup", followup.getId(), "pending_human");
        }
        return toView(followup);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowupView> list(AuthUser current, Long questionId) {
        QuestionDO question = questionDao.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        requireVisible(current, question);
        Long senderId = "private".equals(question.getScope()) ? current.getId() : null;
        return followupDao.findVisible(questionId, senderId)
                .stream().map(this::toView).toList();
    }

    private void requireVisible(AuthUser current, QuestionDO question) {
        if ("public".equals(question.getScope())) {
            if (!PUBLISHED.equals(question.getStatus())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在");
            }
            return;
        }
        if (current == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该私密问题");
        }
        boolean isAuthor = current.getId().equals(question.getAuthorId());
        boolean isAssignedHr = current.getId().equals(question.getAssignedHrId())
                && HR_VISIBLE_STATES.contains(question.getStatus());
        boolean isOperator = "operator".equals(current.getRole()) || "admin".equals(current.getRole());
        if (!isAuthor && !isAssignedHr && !isOperator) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该私密问题");
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
