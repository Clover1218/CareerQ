package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.InteractionDao;
import com.careerq.dao.PublicPostDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dataobject.AnswerDO;
import com.careerq.dataobject.InteractionDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.security.AuthUser;
import com.careerq.service.InteractionService;
import com.careerq.service.InteractionService.Request;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class InteractionServiceImpl implements InteractionService {

    private static final Set<String> TARGET_TYPES = Set.of("answer", "question", "post");
    private static final Set<String> TYPES = Set.of("like", "useful", "favorite");

    private final InteractionDao interactionDao;
    private final AnswerDao answerDao;
    private final QuestionDao questionDao;
    private final PublicPostDao publicPostDao;

    public InteractionServiceImpl(InteractionDao interactionDao,
                                  AnswerDao answerDao,
                                  QuestionDao questionDao,
                                  PublicPostDao publicPostDao) {
        this.interactionDao = interactionDao;
        this.answerDao = answerDao;
        this.questionDao = questionDao;
        this.publicPostDao = publicPostDao;
    }

    @Override
    @Transactional
    public void interact(AuthUser user, Request request) {
        String targetType = normalizeTargetType(request.targetType());
        String type = normalizeType(request.type());
        requireSupported(targetType, type);
        requireVisibleTarget(user, targetType, request.targetId());

        InteractionDO interaction = new InteractionDO();
        interaction.setUserId(user.getId());
        interaction.setTargetType(targetType);
        interaction.setTargetId(request.targetId());
        interaction.setType(type);
        try {
            interactionDao.insert(interaction);
        } catch (DuplicateKeyException duplicate) {
            return; // 已点过：幂等，不重复计数
        }
        applyCount(targetType, type, request.targetId(), +1);
    }

    @Override
    @Transactional
    public void uninteract(AuthUser user, Request request) {
        String targetType = normalizeTargetType(request.targetType());
        String type = normalizeType(request.type());
        requireSupported(targetType, type);
        requireVisibleTarget(user, targetType, request.targetId());

        int deleted = interactionDao.delete(user.getId(), targetType, request.targetId(), type);
        if (deleted > 0) {
            applyCount(targetType, type, request.targetId(), -1);
        }
    }

    private void applyCount(String targetType, String type, Long targetId, int delta) {
        if ("favorite".equals(type)) {
            return; // 收藏不维护计数
        }
        boolean updated;
        switch (targetType) {
            case "answer" -> updated = switch (type) {
                case "like" -> delta > 0 ? answerDao.incrementLike(targetId) > 0 : answerDao.decrementLike(targetId) > 0;
                case "useful" -> delta > 0 ? answerDao.incrementUseful(targetId) > 0 : answerDao.decrementUseful(targetId) > 0;
                default -> false;
            };
            case "question" -> updated = switch (type) {
                case "like" -> delta > 0 ? questionDao.incrementLike(targetId) > 0 : questionDao.decrementLike(targetId) > 0;
                case "useful" -> delta > 0 ? questionDao.incrementUseful(targetId) > 0 : questionDao.decrementUseful(targetId) > 0;
                default -> false;
            };
            case "post" -> updated = switch (type) {
                case "like" -> delta > 0 ? publicPostDao.incrementLike(targetId) > 0 : publicPostDao.decrementLike(targetId) > 0;
                default -> false;
            };
            default -> updated = false;
        }
        if (!updated) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "互动目标不存在");
        }
    }

    private void requireSupported(String targetType, String type) {
        Set<String> supported = switch (targetType) {
            case "post" -> Set.of("like", "favorite");
            default -> Set.of("like", "useful", "favorite");
        };
        if (!supported.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM,
                    "post 只支持 like/favorite，其它目标支持 like/useful/favorite");
        }
    }

    private String normalizeTargetType(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        if (!TARGET_TYPES.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "targetType 不合法");
        }
        return value;
    }

    private String normalizeType(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        if (!TYPES.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "type 不合法");
        }
        return value;
    }

    private void requireVisibleTarget(AuthUser user, String targetType, Long targetId) {
        if ("post".equals(targetType)) {
            return;
        }
        Long questionId = targetId;
        if ("answer".equals(targetType)) {
            AnswerDO answer = answerDao.findById(targetId)
                    .filter(item -> "published".equals(item.getStatus()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回答不存在"));
            questionId = answer.getQuestionId();
        }
        QuestionDO question = questionDao.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        if ("private".equals(question.getScope())) {
            boolean participant = user.getId().equals(question.getAuthorId())
                    || user.getId().equals(question.getAssignedHrId());
            if (!participant || Set.of("pending_human", "rejected", "removed").contains(question.getStatus())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "互动目标不存在");
            }
        } else if (!"published".equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "互动目标不存在");
        }
    }
}