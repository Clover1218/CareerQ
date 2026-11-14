package com.careerq.interaction;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.InteractionDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dataobject.InteractionDO;
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
    private static final Set<String> TYPES = Set.of("like", "favorite", "useful");

    private final InteractionDao interactionDao;
    private final AnswerDao answerDao;
    private final QuestionDao questionDao;

    public InteractionServiceImpl(InteractionDao interactionDao,
                                  AnswerDao answerDao,
                                  QuestionDao questionDao) {
        this.interactionDao = interactionDao;
        this.answerDao = answerDao;
        this.questionDao = questionDao;
    }


    @Override
    @Transactional
    public void interact(AuthUser user, Request request) {
        String targetType = request.targetType().trim().toLowerCase();
        String type = request.type().trim().toLowerCase();
        if (!TARGET_TYPES.contains(targetType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "targetType 不合法");
        }
        if (!TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "type 不合法");
        }
        InteractionDO interaction = new InteractionDO();
        interaction.setUserId(user.getId());
        interaction.setTargetType(targetType);
        interaction.setTargetId(request.targetId());
        interaction.setType(type);
        try {
            interactionDao.insert(interaction);
        } catch (DuplicateKeyException duplicate) {
            return;
        }

        Long targetId = request.targetId();
        if ("answer".equals(targetType)) {
            boolean exists = switch (type) {
                case "like" -> answerDao.incrementLike(targetId) > 0;
                case "useful" -> answerDao.incrementUseful(targetId) > 0;
                default -> answerDao.findById(targetId).isPresent();
            };
            if (!exists) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "回答不存在");
            }
        } else if ("question".equals(targetType) && "useful".equals(type)
                && questionDao.incrementUseful(targetId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在");
        }
    }
}
