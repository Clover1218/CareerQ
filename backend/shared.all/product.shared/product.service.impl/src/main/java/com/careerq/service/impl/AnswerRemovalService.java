package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dataobject.AnswerDO;
import com.careerq.security.AuthUser;
import com.careerq.service.HrNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/** 回答与追问回复共用的移除逻辑，必须与调用方的业务操作处于同一事务。 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AnswerRemovalService {
    private final AnswerDao answerDao;
    private final QuestionDao questionDao;
    private final HrNotificationService notifications;

    public AnswerRemovalService(AnswerDao answerDao, QuestionDao questionDao, HrNotificationService notifications) {
        this.answerDao = answerDao;
        this.questionDao = questionDao;
        this.notifications = notifications;
    }

    /** 与新建回答保持相同锁顺序：先问题，后回答，避免并发发布/移除时锁顺序反转。 */
    public AnswerDO lockAnswer(Long answerId) {
        AnswerDO reference = answerDao.findById(answerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回答不存在"));
        questionDao.findByIdForUpdate(reference.getQuestionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        return answerDao.findByIdForUpdate(answerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回答不存在"));
    }

    public AnswerDO withdraw(AuthUser author, Long answerId) {
        AnswerDO answer = lockAnswer(answerId);
        if (!author.getId().equals(answer.getAuthorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能撤销自己的回答");
        }
        if ("removed".equals(answer.getStatus())) {
            String message = switch (answer.getRemovalSource() == null ? "unknown" : answer.getRemovalSource()) {
                case "operator" -> "回答已被运营下架，不能再撤销";
                case "author" -> "回答已由作者撤销";
                default -> "回答已移除，不能再撤销";
            };
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
        if (!Set.of("published", "pending_human", "pending_machine").contains(answer.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可撤销");
        }
        return remove(answer, "author", author.getId());
    }

    public AnswerDO removeByOperator(AuthUser operator, Long answerId) {
        if (!Set.of("operator", "admin").contains(operator.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有运营可以下架回答");
        }
        AnswerDO answer = lockAnswer(answerId);
        // 多个举报或重复下架只处理一次，保留第一次移除的真实来源与时间。
        if ("removed".equals(answer.getStatus())) return answer;
        AnswerDO removed = remove(answer, "operator", operator.getId());
        notifications.answerRemoved(answerId);
        return removed;
    }

    private AnswerDO remove(AnswerDO answer, String source, Long actorId) {
        boolean published = "published".equals(answer.getStatus());
        LocalDateTime removedAt = LocalDateTime.now();
        if (answerDao.markRemoved(answer.getId(), source, actorId, removedAt) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "回答状态已变化，请刷新后重试");
        }
        if (published) questionDao.decrementAnswer(answer.getQuestionId());
        answer.setStatus("removed");
        answer.setRemovalSource(source);
        answer.setRemovedBy(actorId);
        answer.setRemovedAt(removedAt);
        return answer;
    }
}
