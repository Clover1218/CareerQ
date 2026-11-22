package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.*;
import com.careerq.dataobject.AnswerDO;
import com.careerq.dataobject.FollowupDO;
import com.careerq.dataobject.HrNotificationDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.hr.HrNotificationDtos.*;
import com.careerq.security.AuthUser;
import com.careerq.service.HrNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class HrNotificationServiceImpl implements HrNotificationService {
    private final HrNotificationDao notifications;
    private final UserDao users;
    private final QuestionDao questions;
    private final AnswerDao answers;
    private final FollowupDao followups;

    public HrNotificationServiceImpl(HrNotificationDao notifications, UserDao users, QuestionDao questions,
                                      AnswerDao answers, FollowupDao followups) {
        this.notifications = notifications;
        this.users = users;
        this.questions = questions;
        this.answers = answers;
        this.followups = followups;
    }

    private void requireHr(AuthUser user) {
        if (!"hr".equals(user.getRole()) || users.findById(user.getId()).filter(u -> "hr".equals(u.getRole())).isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅 HR 可访问此消息中心");
        }
    }

    @Override
    public HrNotificationPage list(AuthUser hr, String category, boolean unreadOnly, Long cursor, int limit) {
        requireHr(hr);
        if (!Set.of("all", "followup", "system").contains(category) || cursor != null && cursor <= 0 || limit < 1 || limit > 50) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "消息筛选或翻页参数无效");
        }
        long snapshot = notifications.snapshot(hr.getId());
        List<HrNotificationDO> rows = notifications.list(hr.getId(), category, unreadOnly, cursor, snapshot, limit + 1);
        List<HrNotificationDO> page = rows.stream().limit(limit).toList();
        return new HrNotificationPage(page.stream().map(this::toView).toList(),
                rows.size() > limit ? page.get(page.size() - 1).getId() : null, snapshot);
    }

    @Override
    public HrUnreadCounts unread(AuthUser hr) {
        requireHr(hr);
        return notifications.unread(hr.getId());
    }

    @Override
    @Transactional
    public HrNotification read(AuthUser hr, Long id) {
        requireHr(hr);
        notifications.find(hr.getId(), id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "消息不存在"));
        notifications.read(hr.getId(), id);
        return toView(notifications.find(hr.getId(), id).orElseThrow());
    }

    @Override
    @Transactional
    public void readAll(AuthUser hr, long throughId) {
        requireHr(hr);
        notifications.readAll(hr.getId(), throughId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void followupPublished(Long followupId) {
        FollowupDO followup = followups.findById(followupId).orElse(null);
        if (followup == null || !"published".equals(followup.getStatus()) || !"student".equals(followup.getSenderRole())) return;
        AnswerDO root = answers.findById(followup.getRootAnswerId()).orElse(null);
        QuestionDO question = questions.findById(followup.getQuestionId()).orElse(null);
        if (question == null || !"published".equals(question.getStatus()) || root == null
                || !followup.getQuestionId().equals(root.getQuestionId()) || root.getFollowupId() != null
                || !followup.getHrId().equals(root.getAuthorId())) return;
        emit(followup.getHrId(), "followup_published", "followup", followup.getQuestionId(),
                root.getId(), followup.getId(), "followup_published:" + followupId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void answerReviewed(Long answerId, String decision) {
        if (!Set.of("approve", "reject").contains(decision)) return;
        AnswerDO answer = answers.findById(answerId).orElse(null);
        if (answer == null) return;
        String type = "approve".equals(decision) ? "answer_approved" : "answer_rejected";
        if (!("approve".equals(decision) ? "published" : "rejected").equals(answer.getStatus())) return;
        emit(answer.getAuthorId(), type, "system", answer.getQuestionId(), answerId,
                answer.getFollowupId(), type + ":" + answerId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void answerRemoved(Long answerId) {
        AnswerDO answer = answers.findById(answerId).orElse(null);
        if (answer == null || !"removed".equals(answer.getStatus()) || !"operator".equals(answer.getRemovalSource())) return;
        emit(answer.getAuthorId(), "answer_removed", "system", answer.getQuestionId(), answerId,
                answer.getFollowupId(), "answer_removed:" + answerId);
    }

    private void emit(Long recipient, String type, String category, Long question, Long answer, Long followup, String eventKey) {
        if (recipient == null || users.findById(recipient).filter(user -> "hr".equals(user.getRole())).isEmpty()) return;
        HrNotificationDO notification = new HrNotificationDO();
        notification.setRecipientId(recipient);
        notification.setType(type);
        notification.setCategory(category);
        notification.setQuestionId(question);
        notification.setAnswerId(answer);
        notification.setFollowupId(followup);
        notification.setEventKey(eventKey);
        notification.setCreatedAt(LocalDateTime.now());
        notifications.insert(notification);
    }

    private HrNotification toView(HrNotificationDO notification) {
        QuestionDO question = questions.findById(notification.getQuestionId()).orElse(null);
        boolean visibleQuestion = question != null && "published".equals(question.getStatus());
        AnswerDO answer = notification.getAnswerId() == null ? null : answers.findById(notification.getAnswerId()).orElse(null);
        boolean ownAnswer = answer != null && notification.getRecipientId().equals(answer.getAuthorId());
        String destination = "unavailable";
        String subject = answer != null && answer.getFollowupId() != null ? "追问回复" : "回答";
        String title;
        String message;
        switch (notification.getType()) {
            case "followup_published" -> {
                title = "学生对你的回答发起了追问";
                message = "点击查看具体追问，继续分享你的经验。";
                FollowupDO followup = followups.findById(notification.getFollowupId()).orElse(null);
                if (visibleQuestion && ownAnswer && followup != null && "published".equals(followup.getStatus())
                        && notification.getRecipientId().equals(followup.getHrId())
                        && answer.getId().equals(followup.getRootAnswerId())
                        && Set.of("published", "removed").contains(answer.getStatus())) destination = "question";
            }
            case "answer_approved" -> {
                title = "你的" + subject + "审核通过";
                message = "内容已通过人工审核。";
                if (ownAnswer) destination = visibleQuestion && "published".equals(answer.getStatus()) ? "question" : "answer_record";
            }
            case "answer_rejected" -> {
                title = "你的" + subject + "审核未通过";
                message = "内容未通过平台审核，请调整表达后重新提交。";
                if (ownAnswer) destination = "answer_record";
            }
            case "answer_removed" -> {
                title = "你的" + subject + "已被运营下架";
                message = "内容经运营处理后已停止公开展示，可查看对应记录。";
                if (ownAnswer) destination = "answer_record";
            }
            default -> { title = "系统通知"; message = "请查看相关内容的当前状态。"; }
        }
        if ("unavailable".equals(destination)) message = "相关内容已不可查看，原问题或追问可能已被撤销或下架。";
        // 通知不存正文快照；每次读取重新判断可见性，不返回审核内部备注。
        return new HrNotification(notification.getId(), notification.getType(), notification.getCategory(), title, message,
                visibleQuestion ? question.getTitle() : null, notification.getQuestionId(), notification.getAnswerId(),
                notification.getFollowupId(), destination, notification.getCreatedAt(), notification.getReadAt());
    }
}
