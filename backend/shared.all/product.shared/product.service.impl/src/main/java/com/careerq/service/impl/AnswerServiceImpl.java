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

    public AnswerServiceImpl(AnswerDao answerDao,
                             FollowupDao followupDao,
                             QuestionDao questionDao,
                             UserDao userDao,
                             ModerationService moderationService,
                             HrProfileDao hrProfileDao) {
        this.answerDao = answerDao;
        this.followupDao = followupDao;
        this.questionDao = questionDao;
        this.userDao = userDao;
        this.moderationService = moderationService;
        this.hrProfileDao = hrProfileDao;
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
        } else if (!VISIBLE.equals(question.getVisibility())) {
            throw new BusinessException(ErrorCode.CONFLICT, "问题当前不可回答");
        }

        boolean highFrequency = answerDao.countByAuthorSince(
                hr.getId(), LocalDateTime.now().minusHours(1)) >= 10;
        ModerationPlan plan = moderationService.evaluate("answer", null, request.body(),
                question.getTitle(), hr.getRole(), highFrequency);

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
        }
        return toView(answer);
    }

    @Override
    @Transactional
    public AnswerView withdraw(AuthUser author, Long answerId) {
        AnswerDO answer = answerDao.findByIdForUpdate(answerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回答不存在"));
        if (!author.getId().equals(answer.getAuthorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能撤销自己的回答");
        }
        if ("WITHDRAWN".equals(answer.getModerationStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "回答已撤销");
        }
        if (!ACTIVE_STATUSES.contains(answer.getModerationStatus())
                || !Set.of("VISIBLE", "HIDDEN").contains(answer.getVisibility())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可撤销");
        }
        boolean visible = VISIBLE.equals(answer.getVisibility());
        answerDao.withdraw(answerId);
        if (visible) {
            questionDao.decrementAnswer(answer.getQuestionId());
        }
        answer.setModerationStatus("WITHDRAWN");
        answer.setVisibility("REMOVED");
        return toView(answer);
    }

    private AnswerView toView(AnswerDO answer) {
        return new AnswerView(
                answer.getId(),
                answer.getQuestionId(),
                answer.getFollowupId(),
                answer.getBody(),
                answer.getLengthType(),
                DisplayStatus.of(answer.getModerationStatus(), answer.getVisibility()).name(),
                authorOf(answer.getAuthorId()),
                answer.getLikeCount(),
                answer.getUsefulCount(),
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
