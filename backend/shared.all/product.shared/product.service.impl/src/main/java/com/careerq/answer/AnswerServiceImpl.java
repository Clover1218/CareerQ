package com.careerq.answer;

import com.careerq.answer.AnswerDtos.AnswerView;
import com.careerq.answer.AnswerDtos.CreateRequest;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.common.UserBrief;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.AnswerDO;
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
    private final QuestionDao questionDao;
    private final UserDao userDao;
    private final ModerationService moderationService;

    public AnswerServiceImpl(AnswerDao answerDao,
                             QuestionDao questionDao,
                             UserDao userDao,
                             ModerationService moderationService) {
        this.answerDao = answerDao;
        this.questionDao = questionDao;
        this.userDao = userDao;
        this.moderationService = moderationService;
    }

    @Override
    @Transactional
    public AnswerView create(AuthUser hr, Long questionId, CreateRequest request) {
        QuestionDO question = questionDao.findByIdForUpdate(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        if ("private".equals(question.getScope())) {
            boolean assignedToMe = hr.getId().equals(question.getAssignedHrId());
            if (!assignedToMe || !"assigned".equals(question.getStatus())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "该私密问题未指派给你");
            }
        } else if (!PUBLISHED.equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "问题当前不可回答");
        }

        AnswerDO answer = new AnswerDO();
        answer.setQuestionId(questionId);
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
                answer.getBody(),
                answer.getLengthType(),
                answer.getStatus(),
                userDao.findById(answer.getAuthorId())
                        .map(user -> new UserBrief(user.getId(), user.getNickname()))
                        .orElse(new UserBrief(answer.getAuthorId(), "HR")),
                answer.getLikeCount(),
                answer.getUsefulCount(),
                answer.getCreatedAt());
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
