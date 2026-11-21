package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.UserBrief;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.FollowupDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.FollowupDO;
import com.careerq.dataobject.AnswerDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.followup.FollowupDtos.CreateRequest;
import com.careerq.followup.FollowupDtos.FollowupView;
import com.careerq.security.AuthUser;
import com.careerq.service.FollowupService;
import com.careerq.service.HrNotificationService;
import com.careerq.service.ModerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class FollowupServiceImpl implements FollowupService {

    private static final String PUBLISHED = "published";

    private final FollowupDao followupDao;
    private final QuestionDao questionDao;
    private final UserDao userDao;
    private final AnswerDao answerDao;
    private final HrNotificationService notifications;
    private final ModerationService moderationService;

    public FollowupServiceImpl(FollowupDao followupDao, QuestionDao questionDao, UserDao userDao,
                               AnswerDao answerDao, HrNotificationService notifications,
                               ModerationService moderationService) {
        this.followupDao = followupDao;
        this.questionDao = questionDao;
        this.userDao = userDao;
        this.answerDao = answerDao;
        this.notifications = notifications;
        this.moderationService = moderationService;
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
            throw new BusinessException(ErrorCode.INVALID_PARAM, "当前问题不可追问");
        }
        requireVisible(sender, question);
        if (!sender.getId().equals(question.getAuthorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有题主可以追问");
        }
        Long rootAnswerId = request.rootAnswerId();
        if (rootAnswerId == null || rootAnswerId <= 0 || request.hrId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "请先选择要追问的具体回答");
        }
        AnswerDO root = answerDao.findByIdForUpdate(rootAnswerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "目标回答不存在"));
        if (!questionId.equals(root.getQuestionId()) || root.getFollowupId() != null
                || !PUBLISHED.equals(root.getStatus()) || !request.hrId().equals(root.getAuthorId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "只能追问该 HR 在此问题下已发布的主回答");
        }

        FollowupDO followup = new FollowupDO();
        followup.setQuestionId(questionId);
        followup.setHrId(request.hrId());
        followup.setRootAnswerId(rootAnswerId);
        followup.setSenderId(sender.getId());
        followup.setSenderRole(sender.getRole());
        followup.setBody(request.body());
        followup.setScope(question.getScope());
        followup.setStatus(moderationService.decide(request.body()));
        followupDao.insert(followup);
        if (PUBLISHED.equals(followup.getStatus())) {
            questionDao.incrementFollowup(questionId);
            notifications.followupPublished(followup.getId());
        } else {
            moderationService.recordMachine("followup", followup.getId(), followup.getStatus());
        }
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
        Long senderId = current == null ? null : current.getId();
        List<FollowupDO> rows = followupDao.findVisible(questionId, senderId);
        return rows.stream().map(this::toView).toList();
    }

    @Override
    @Transactional
    public FollowupView withdraw(AuthUser sender, Long followupId) {
        FollowupDO reference = followupDao.findById(followupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "追问不存在"));
        questionDao.findByIdForUpdate(reference.getQuestionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        FollowupDO followup = followupDao.findByIdForUpdate(followupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "追问不存在"));
        if (!sender.getId().equals(followup.getSenderId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能撤销自己的追问");
        }
        if ("removed".equals(followup.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "追问已撤销");
        }
        if (!Set.of("published", "pending_human", "pending_machine").contains(followup.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可撤销");
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
        if (!"public".equals(question.getScope()) || !PUBLISHED.equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在");
        }
    }

    private FollowupView toView(FollowupDO followup) {
        UserBrief author = userDao.findById(followup.getSenderId())
                .map(user -> new UserBrief(user.getId(), user.getNickname()))
                .orElse(new UserBrief(followup.getSenderId(), "用户"));
        return new FollowupView(
                followup.getId(),
                followup.getQuestionId(),
                followup.getHrId(),
                followup.getRootAnswerId(),
                followup.getSenderRole(),
                "removed".equals(followup.getStatus()) ? "" : followup.getBody(),
                followup.getStatus(),
                followup.getCreatedAt(),
                author,
                "hr".equals(followup.getSenderRole()));
    }
}
