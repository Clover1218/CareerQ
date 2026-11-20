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
import com.careerq.security.AuthUser;
import com.careerq.service.AnswerService;
import com.careerq.service.ModerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnswerServiceImpl implements AnswerService {

    private static final String PUBLISHED = "published";

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
            throw new BusinessException(ErrorCode.INVALID_PARAM, "私密问答已明确不做");
        }
        if (followupId != null) {
            if (!PUBLISHED.equals(question.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前问题不可回复追问");
            }
            FollowupDO followup = followupDao.findByIdForUpdate(followupId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "追问不存在"));
            if (!questionId.equals(followup.getQuestionId())
                    || !"student".equals(followup.getSenderRole())
                    || !"public".equals(followup.getScope())
                    || !PUBLISHED.equals(followup.getStatus())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "追问不存在或不可回复");
            }
            if (!hr.getId().equals(followup.getHrId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "只能回复发给自己线程的追问");
            }
        } else if (!PUBLISHED.equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "问题当前不可回答");
        }

        AnswerDO answer = new AnswerDO();
        answer.setQuestionId(questionId);
        answer.setFollowupId(followupId);
        answer.setAuthorId(hr.getId());
        answer.setBody(request.body());
        answer.setLengthType(normalizeLengthType(request.lengthType()));
        answer.setStatus(moderationService.decide(request.body()));
        answerDao.insert(answer);
        if (!PUBLISHED.equals(answer.getStatus())) {
            moderationService.recordMachine("answer", answer.getId(), answer.getStatus());
        }

        if (PUBLISHED.equals(answer.getStatus())) {
            questionDao.recordAnswer(questionId);
        }
        return toView(answer);
    }

    private AnswerView toView(AnswerDO answer) {
        return new AnswerView(
                answer.getId(),
                answer.getQuestionId(),
                answer.getFollowupId(),
                answer.getBody(),
                answer.getLengthType(),
                answer.getStatus(),
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
