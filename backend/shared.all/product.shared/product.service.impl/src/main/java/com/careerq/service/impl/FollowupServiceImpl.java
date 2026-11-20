package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.UserBrief;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.FollowupDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.FollowupDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.followup.FollowupDtos.CreateRequest;
import com.careerq.followup.FollowupDtos.FollowupView;
import com.careerq.security.AuthUser;
import com.careerq.service.FollowupService;
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
    private final UserDao userDao;
    private final AnswerDao answerDao;

    public FollowupServiceImpl(FollowupDao followupDao, QuestionDao questionDao, UserDao userDao, AnswerDao answerDao) {
        this.followupDao = followupDao;
        this.questionDao = questionDao;
        this.userDao = userDao;
        this.answerDao = answerDao;
    }


    @Override
    @Transactional
    public FollowupView create(AuthUser sender, Long questionId, CreateRequest request) {
        if (!"student".equals(sender.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有学生可以追问");
        }
        QuestionDO question = questionDao.findByIdForUpdate(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        if (!"public".equals(question.getScope())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "私密问答已明确不做");
        }
        requireVisible(sender, question);

        FollowupDO followup = new FollowupDO();
        followup.setQuestionId(questionId);
        followup.setSenderId(sender.getId());
        followup.setSenderRole(sender.getRole());
        followup.setBody(request.body());
        followup.setScope(question.getScope());
        followup.setStatus(PUBLISHED);
        followupDao.insert(followup);
        questionDao.incrementFollowup(questionId);
        return toView(followup);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowupView> list(AuthUser current, Long questionId) {
        QuestionDO question = questionDao.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        // 作者可以查看自己审核中/未通过的公开问题详情，追问列表随之放行；发言仍要求已发表
        boolean ownPublicQuestion = "public".equals(question.getScope())
                && current != null && current.getId().equals(question.getAuthorId());
        if (!ownPublicQuestion) {
            requireVisible(current, question);
        }
        Long senderId = "private".equals(question.getScope()) ? current.getId() : null;
        return followupDao.findVisible(questionId, senderId)
                .stream().map(this::toView).toList();
    }

    @Override
    @Transactional
    public FollowupView withdraw(AuthUser sender, Long questionId, Long followupId) {
        questionDao.findByIdForUpdate(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        FollowupDO followup = followupDao.findByIdForUpdate(followupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "追问不存在"));
        if (!questionId.equals(followup.getQuestionId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "追问不存在");
        }
        if (!sender.getId().equals(followup.getSenderId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能撤销自己的追问");
        }
        if ("removed".equals(followup.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "追问已撤销");
        }
        if (!Set.of("published", "pending_human", "pending_machine").contains(followup.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可撤销");
        }
        if (answerDao.countByFollowupId(followupId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "追问已有 HR 回复，不能撤销");
        }
        boolean published = PUBLISHED.equals(followup.getStatus());
        followupDao.updateStatus(followupId, "removed");
        if (published) {
            questionDao.decrementFollowup(followup.getQuestionId());
        }
        followup.setStatus("removed");
        return toView(followup);
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
        UserBrief author = userDao.findById(followup.getSenderId())
                .map(user -> new UserBrief(user.getId(), user.getNickname()))
                .orElse(new UserBrief(followup.getSenderId(), "用户"));
        return new FollowupView(
                followup.getId(),
                followup.getQuestionId(),
                followup.getSenderRole(),
                followup.getBody(),
                followup.getStatus(),
                followup.getCreatedAt(),
                author,
                "hr".equals(followup.getSenderRole()));
    }
}
