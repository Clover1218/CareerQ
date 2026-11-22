package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.*;
import com.careerq.dataobject.*;
import com.careerq.student.StudentNotificationDtos.*;
import com.careerq.security.AuthUser;
import com.careerq.service.StudentNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class StudentNotificationServiceImpl implements StudentNotificationService {
    private final StudentNotificationDao notifications;
    private final UserDao users;
    private final QuestionDao questions;
    private final AnswerDao answers;
    private final FollowupDao followups;

    public StudentNotificationServiceImpl(StudentNotificationDao notifications, UserDao users,
            QuestionDao questions, AnswerDao answers, FollowupDao followups) {
        this.notifications = notifications; this.users = users; this.questions = questions;
        this.answers = answers; this.followups = followups;
    }
    private void requireStudent(AuthUser user) {
        if (!"student".equals(user.getRole()) || users.findById(user.getId()).filter(u -> "student".equals(u.getRole())).isEmpty())
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅学生可访问此消息中心");
    }
    @Override
    public StudentNotificationPage list(AuthUser user, String category, boolean unreadOnly, Long cursor, int limit) {
        requireStudent(user);
        if (!Set.of("all", "reply", "like", "system").contains(category) || cursor != null && cursor <= 0 || limit < 1 || limit > 50)
            throw new BusinessException(ErrorCode.INVALID_PARAM, "消息筛选或翻页参数无效");
        long snapshot = notifications.snapshot(user.getId());
        List<StudentNotificationDO> rows = notifications.list(user.getId(), category, unreadOnly, cursor, snapshot, limit + 1);
        var page = rows.stream().limit(limit).toList();
        return new StudentNotificationPage(page.stream().map(this::toView).toList(),
                rows.size() > limit ? page.get(page.size() - 1).getId() : null, snapshot);
    }
    @Override
    public StudentUnreadCounts unread(AuthUser user) { requireStudent(user); return notifications.unread(user.getId()); }
    @Override @Transactional
    public StudentNotification read(AuthUser user, Long id) {
        requireStudent(user);
        notifications.find(user.getId(), id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "消息不存在"));
        notifications.read(user.getId(), id);
        return toView(notifications.find(user.getId(), id).orElseThrow());
    }
    @Override @Transactional
    public void readAll(AuthUser user, long throughId) { requireStudent(user); notifications.readAll(user.getId(), throughId); }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void answerPublished(Long answerId) {
        AnswerDO answer = answers.findById(answerId).orElse(null);
        if (answer == null || !"published".equals(answer.getStatus())) return;
        QuestionDO question = questions.findById(answer.getQuestionId()).orElse(null);
        if (question == null || !"public".equals(question.getScope()) || !"published".equals(question.getStatus())) return;
        if (answer.getFollowupId() != null) {
            FollowupDO followup = followups.findById(answer.getFollowupId()).orElse(null);
            if (followup == null || !question.getId().equals(followup.getQuestionId()) || followup.getRootAnswerId() == null
                    || !question.getAuthorId().equals(followup.getSenderId()) || !Set.of("published", "removed").contains(followup.getStatus())) return;
        }
        emit(question.getAuthorId(), "answer_published", "reply", question.getId(), answerId,
                answer.getFollowupId(), "answer_published:" + answerId);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void questionLiked(Long questionId, Long actorId) {
        QuestionDO question = questions.findById(questionId).orElse(null);
        if (question == null || actorId.equals(question.getAuthorId()) || !"public".equals(question.getScope())
                || !"published".equals(question.getStatus())) return;
        emit(question.getAuthorId(), "question_liked", "like", questionId, null, null,
                "question_liked:" + questionId + ":" + actorId);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void contentReviewed(String targetType, Long targetId, String decision) {
        if (!Set.of("question", "followup").contains(targetType) || !Set.of("approve", "reject", "remove").contains(decision)) return;
        Long questionId; Long recipient; Long followupId = null;
        if ("question".equals(targetType)) {
            QuestionDO question = questions.findById(targetId).orElse(null);
            if (question == null || !"public".equals(question.getScope())) return;
            questionId = question.getId(); recipient = question.getAuthorId();
        } else {
            FollowupDO followup = followups.findById(targetId).orElse(null);
            if (followup == null || followup.getRootAnswerId() == null || !"student".equals(followup.getSenderRole())) return;
            questionId = followup.getQuestionId(); recipient = followup.getSenderId(); followupId = targetId;
        }
        String type = targetType + "_" + switch (decision) { case "approve" -> "approved"; case "reject" -> "rejected"; default -> "removed"; };
        emit(recipient, type, "system", questionId, null, followupId, type + ":" + targetId);
    }
    private void emit(Long recipient, String type, String category, Long question, Long answer, Long followup, String key) {
        if (recipient == null || users.findById(recipient).filter(u -> "student".equals(u.getRole())).isEmpty()) return;
        StudentNotificationDO notice = new StudentNotificationDO();
        notice.setRecipientId(recipient); notice.setType(type); notice.setCategory(category); notice.setQuestionId(question);
        notice.setAnswerId(answer); notice.setFollowupId(followup); notice.setEventKey(key); notice.setCreatedAt(LocalDateTime.now());
        notifications.insert(notice);
    }
    private StudentNotification toView(StudentNotificationDO notice) {
        QuestionDO question = questions.findById(notice.getQuestionId()).orElse(null);
        boolean owned = question != null && "public".equals(question.getScope()) && notice.getRecipientId().equals(question.getAuthorId());
        boolean published = owned && "published".equals(question.getStatus());
        boolean available = false;
        String title; String message;
        if ("reply".equals(notice.getCategory())) {
            AnswerDO answer = notice.getAnswerId() == null ? null : answers.findById(notice.getAnswerId()).orElse(null);
            boolean valid = answer != null && notice.getQuestionId().equals(answer.getQuestionId()) && "published".equals(answer.getStatus());
            if (valid && answer.getFollowupId() != null) {
                FollowupDO followup = followups.findById(answer.getFollowupId()).orElse(null);
                valid = followup != null && followup.getRootAnswerId() != null && notice.getRecipientId().equals(followup.getSenderId())
                        && question != null && question.getId().equals(followup.getQuestionId())
                        && Set.of("published", "removed").contains(followup.getStatus());
            }
            String author = valid ? users.findById(answer.getAuthorId()).map(UserDO::getNickname).orElse("HR") : "HR";
            title = notice.getFollowupId() == null ? "你的问题收到新回答" : "你的追问收到新回复";
            String preview = valid && published ? answer.getBody().replaceAll("\\s+", " ").trim() : "";
            if (preview.length() > 120) preview = preview.substring(0, 120) + "…";
            message = valid && published ? "HR · " + author + "：" + preview : "这条回答已撤销、下架或暂时无法查看。";
            available = valid && published;
        } else if ("like".equals(notice.getCategory())) {
            title = "有人赞了你的问题"; message = "你的问题获得了新的赞。"; available = published;
        } else {
            String subject = notice.getFollowupId() == null ? "问题" : "追问";
            String result = notice.getType().endsWith("_approved") ? "审核通过" : notice.getType().endsWith("_rejected") ? "审核未通过" : "已被运营下架";
            title = "你的" + subject + result;
            message = notice.getType().endsWith("_approved") ? "内容已通过人工审核。" : notice.getType().endsWith("_rejected")
                    ? "内容未通过平台审核，请调整表达后重新提交。" : "内容经运营处理后停止公开展示。";
            available = owned;
            if (notice.getFollowupId() != null) available = owned && followups.findById(notice.getFollowupId())
                    .filter(f -> notice.getRecipientId().equals(f.getSenderId()) && notice.getQuestionId().equals(f.getQuestionId())).isPresent();
        }
        if (!available && !"reply".equals(notice.getCategory())) message = "相关内容已不可查看。";
        return new StudentNotification(notice.getId(), notice.getType(), notice.getCategory(), title, message,
                available ? question.getTitle() : null, notice.getQuestionId(), notice.getAnswerId(), notice.getFollowupId(),
                available ? "question" : "unavailable", notice.getCreatedAt(), notice.getReadAt());
    }
}
