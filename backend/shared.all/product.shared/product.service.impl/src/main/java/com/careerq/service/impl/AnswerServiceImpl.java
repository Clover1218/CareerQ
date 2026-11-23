package com.careerq.service.impl;

import com.careerq.answer.AnswerDtos.AnswerView;
import com.careerq.answer.AnswerDtos.CreateRequest;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.UserBrief;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.FollowupDao;
import com.careerq.dao.HrProfileDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.AnswerDO;
import com.careerq.dataobject.FollowupDO;
import com.careerq.dataobject.HrProfileDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.moderation.DisplayStatus;
import com.careerq.moderation.ModerationPlan;
import com.careerq.security.AuthUser;
import com.careerq.service.AnswerService;
import com.careerq.service.ModerationService;
import com.careerq.service.StudentNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class AnswerServiceImpl implements AnswerService {

    private static final String VISIBLE = "VISIBLE";
    private static final Set<String> ACTIVE_STATUSES = Set.of("L1", "L2", "L3", "APPROVED");

    private final AnswerDao answerDao;
    private final FollowupDao followupDao;
    private final QuestionDao questionDao;
    private final UserDao userDao;
    private final ModerationService moderationService;
    private final HrProfileDao hrProfileDao;
    private final AnswerRemovalService answerRemovalService;
    private final StudentNotificationService studentNotifications;

    public AnswerServiceImpl(AnswerDao answerDao,
                             FollowupDao followupDao,
                             QuestionDao questionDao,
                             UserDao userDao,
                             ModerationService moderationService,
                             HrProfileDao hrProfileDao,
                             AnswerRemovalService answerRemovalService,
                             StudentNotificationService studentNotifications) {
        this.answerDao = answerDao;
        this.followupDao = followupDao;
        this.questionDao = questionDao;
        this.userDao = userDao;
        this.moderationService = moderationService;
        this.hrProfileDao = hrProfileDao;
        this.answerRemovalService = answerRemovalService;
        this.studentNotifications = studentNotifications;
    }

    @Override
    @Transactional
    public AnswerView create(AuthUser hr, Long questionId, CreateRequest request) {
        return create(hr, questionId, null, request);
    }

    @Override
    @Transactional
    public AnswerView replyToFollowup(AuthUser hr, Long questionId, Long followupId, CreateRequest request) {
        return create(hr, questionId, followupId, request);
    }

    private AnswerView create(AuthUser hr, Long questionId, Long followupId, CreateRequest request) {
        if (!"hr".equals(hr.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有 HR 可以提交回答");
        }
        QuestionDO question = questionDao.findByIdForUpdate(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        if (!"public".equals(question.getScope())) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "当前问题不可回答");
        }
        if (followupId != null) {
            if (!VISIBLE.equals(question.getVisibility())) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前问题不可回复追问");
            }
            FollowupDO followup = followupDao.findByIdForUpdate(followupId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "追问不存在"));
            if (!questionId.equals(followup.getQuestionId())
                    || !"student".equals(followup.getSenderRole())
                    || !"public".equals(followup.getScope())
                    || !VISIBLE.equals(followup.getVisibility())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "追问不存在或不可回复");
            }
            if (!hr.getId().equals(followup.getHrId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "只能回复发给自己线程的追问");
            }
AnswerDO root = answerDao.findById(followup.getRootAnswerId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "原回答不存在"));
            if (!questionId.equals(root.getQuestionId()) || root.getFollowupId() != null
                    || !hr.getId().equals(root.getAuthorId())
                    || !Set.of("VISIBLE", "REMOVED").contains(root.getVisibility())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "追问对应的主回答不可用");
            }
            Long latestFollowupId = followupDao.findLatestId(questionId, hr.getId(),
                    followup.getRootAnswerId());
            if (latestFollowupId == null || !latestFollowupId.equals(followupId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "请先回复该线程最新的追问");
            }
        } else if (!VISIBLE.equals(question.getVisibility())) {
            throw new BusinessException(ErrorCode.CONFLICT, "问题当前不可回答");
        }

        boolean highFrequency = answerDao.countByAuthorSince(
                hr.getId(), LocalDateTime.now().minusHours(1)) >= 10;
        ModerationPlan plan = moderationService.evaluate("answer", null, request.body(),
                question.getTitle(), hr.getRole(), highFrequency);
        // 同一追问只保留一条公开回复；后续补充回复仅在进入审核（不可见）时允许。
        if (followupId != null && "VISIBLE".equals(plan.visibility().name())
                && answerDao.hasVisibleReply(questionId, followupId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该追问已有公开回复，不能重复回复");
        }

        AnswerDO answer = new AnswerDO();
        answer.setQuestionId(questionId);
        answer.setFollowupId(followupId);
        answer.setAuthorId(hr.getId());
        answer.setBody(request.body());
        answer.setLengthType(normalizeLengthType(request.lengthType()));
        answer.setModerationStatus(plan.moderationStatus().name());
        answer.setVisibility(plan.visibility().name());
        answer.setRiskLevel(plan.riskLevel().name());
        answer.setModerationReason(plan.reason());
        answer.setModeratedAt(LocalDateTime.now());
        answerDao.insert(answer);
        moderationService.recordPlan("answer", answer.getId(), plan);

        if (VISIBLE.equals(answer.getVisibility())) {
            questionDao.recordAnswer(questionId);
            studentNotifications.answerPublished(answer.getId());
        }
        return toView(answer);
    }

    @Override
    @Transactional
    public AnswerView withdraw(AuthUser author, Long answerId) {
        return toView(answerRemovalService.withdraw(author, answerId));
    }

    private AnswerView toView(AnswerDO answer) {
        boolean hidden = "removed".equals(answer.getStatus());
        return new AnswerView(
                answer.getId(),
                answer.getQuestionId(),
                answer.getFollowupId(),
                hidden ? "" : answer.getBody(),
                answer.getLengthType(),
                DisplayStatus.of(answer.getModerationStatus(), answer.getVisibility()).name(),
                answer.getStatus(),
                answer.getRemovalSource(),
                authorOf(answer.getAuthorId()),
                hidden ? 0 : answer.getLikeCount(),
                hidden ? 0 : answer.getUsefulCount(),
                false,
                false,
                answer.getCreatedAt());
    }

    private UserBrief authorOf(Long userId) {
        return userDao.findById(userId)
                .map(user -> {
                    if (!"hr".equals(user.getRole())) {
                        return new UserBrief(user.getId(), user.getNickname());
                    }
                    HrProfileDO profile = hrProfileDao.findByUserId(user.getId()).orElse(null);
                    return new UserBrief(user.getId(), user.getNickname(),
                            profile == null ? null : profile.getCompanyDisplay(),
                            profile == null ? null : profile.getPosition());
                })
                .orElse(new UserBrief(userId, "HR"));
    }

    private String normalizeLengthType(String lengthType) {
        if (lengthType == null || lengthType.isBlank()) {
            return "detailed";
        }
        String value = lengthType.trim().toLowerCase();
        if (!"one_liner".equals(value) && !"detailed".equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "lengthType 只能是 one_liner 或 detailed");
        }
        return value;
    }
}
