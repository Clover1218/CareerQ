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
import com.careerq.moderation.DisplayStatus;
import com.careerq.moderation.ModerationPlan;
import com.careerq.security.AuthUser;
import com.careerq.service.FollowupService;
import com.careerq.service.HrNotificationService;
import com.careerq.service.ModerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class FollowupServiceImpl implements FollowupService {

    private static final String VISIBLE = "VISIBLE";
    private static final Set<String> ACTIVE_STATUSES = Set.of("L1", "L2", "L3", "APPROVED");

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
                || !VISIBLE.equals(root.getVisibility()) || !request.hrId().equals(root.getAuthorId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "只能追问该 HR 在此问题下已发布的主回答");
        }

        boolean highFrequency = followupDao.countBySenderSince(
                sender.getId(), LocalDateTime.now().minusHours(1)) >= 10;
        ModerationPlan plan = moderationService.evaluate("followup", null, request.body(),
                question.getTitle(), sender.getRole(), highFrequency);

        FollowupDO followup = new FollowupDO();
        followup.setQuestionId(questionId);
        followup.setHrId(request.hrId());
        followup.setRootAnswerId(rootAnswerId);
        followup.setSenderId(sender.getId());
        followup.setSenderRole(sender.getRole());
        followup.setBody(request.body());
        followup.setScope(question.getScope());
followup.setModerationStatus(plan.moderationStatus().name());
        followup.setVisibility(plan.visibility().name());
        followup.setRiskLevel(plan.riskLevel().name());
        followup.setModerationReason(plan.reason());
        followup.setModeratedAt(LocalDateTime.now());
        followupDao.insert(followup);
        moderationService.recordPlan("followup", followup.getId(), plan);
        if (VISIBLE.equals(followup.getVisibility())) {
            questionDao.incrementFollowup(questionId);
            notifications.followupPublished(followup.getId());
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
        boolean operator = current != null
                && ("operator".equals(current.getRole()) || "admin".equals(current.getRole()));
        List<FollowupDO> rows = followupDao.findVisible(questionId, senderId, operator);
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
        if ("WITHDRAWN".equals(followup.getModerationStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "追问已撤销");
        }
        if (!ACTIVE_STATUSES.contains(followup.getModerationStatus())
                || !Set.of("VISIBLE", "HIDDEN").contains(followup.getVisibility())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可撤销");
        }
        boolean visible = VISIBLE.equals(followup.getVisibility());
        followupDao.withdraw(followupId);
        if (visible) {
            questionDao.decrementFollowup(followup.getQuestionId());
        }
        followup.setModerationStatus("WITHDRAWN");
        followup.setVisibility("REMOVED");
        return toView(followup);
    }

    private void requireVisible(AuthUser current, QuestionDO question) {
        if (!"public".equals(question.getScope()) || !VISIBLE.equals(question.getVisibility())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在");
        }
    }

    private FollowupView toView(FollowupDO followup) {
        UserBrief author = userDao.findById(followup.getSenderId())
                .map(user -> new UserBrief(user.getId(), user.getNickname(), user.getAvatarUrl()))
                .orElse(new UserBrief(followup.getSenderId(), "用户"));
        return new FollowupView(
                followup.getId(),
                followup.getQuestionId(),
                followup.getHrId(),
                followup.getRootAnswerId(),
                followup.getSenderRole(),
                "REMOVED".equals(followup.getVisibility()) ? "" : followup.getBody(),
                DisplayStatus.of(followup.getModerationStatus(), followup.getVisibility()).name(),
                followup.getStatus(),
                followup.getCreatedAt(),
                author,
                "hr".equals(followup.getSenderRole()));
    }
}
