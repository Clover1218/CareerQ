package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.FollowupDao;
import com.careerq.dao.ModerationWordDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dao.ReportDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.AnswerDO;
import com.careerq.dataobject.FollowupDO;
import com.careerq.dataobject.ModerationWordDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.dataobject.ReportDO;
import com.careerq.dataobject.UserDO;
import com.careerq.security.AuthUser;
import com.careerq.service.AdminService;
import com.careerq.service.AdminService.DecideRequest;
import com.careerq.service.AdminService.HandleReportRequest;
import com.careerq.service.AdminService.QueueItem;
import com.careerq.service.AdminService.ReportView;
import com.careerq.service.AdminService.WordRequest;
import com.careerq.service.AdminService.WordView;
import com.careerq.service.ModerationService;
import com.careerq.service.HrNotificationService;
import com.careerq.service.StudentNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminServiceImpl implements AdminService {

    private static final String PENDING_HUMAN = "pending_human";
    private static final Set<String> DECISIONS = Set.of("approve", "reject", "remove");

    private final QuestionDao questionDao;
    private final AnswerDao answerDao;
    private final FollowupDao followupDao;
    private final ReportDao reportDao;
    private final ModerationWordDao wordDao;
    private final ModerationService moderationService;
    private final UserDao userDao;
    private final AnswerRemovalService answerRemovalService;
    private final HrNotificationService notifications;
    private final StudentNotificationService studentNotifications;

    public AdminServiceImpl(QuestionDao questionDao,
                            AnswerDao answerDao,
                            FollowupDao followupDao,
                            ReportDao reportDao,
                            ModerationWordDao wordDao,
                            ModerationService moderationService,
                            UserDao userDao,
                            AnswerRemovalService answerRemovalService,
                            HrNotificationService notifications,
                            StudentNotificationService studentNotifications) {
        this.questionDao = questionDao;
        this.answerDao = answerDao;
        this.followupDao = followupDao;
        this.reportDao = reportDao;
        this.wordDao = wordDao;
        this.moderationService = moderationService;
        this.userDao = userDao;
        this.answerRemovalService = answerRemovalService;
        this.notifications = notifications;
        this.studentNotifications = studentNotifications;
    }


    @Override
    @Transactional(readOnly = true)
    public List<QueueItem> queue() {
        List<QueueItem> items = new ArrayList<>();
        for (QuestionDO question : questionDao.findByStatusOrderByIdAsc(PENDING_HUMAN)) {
            items.add(new QueueItem("question", question.getId(), question.getTitle(),
                    snippet(question.getBody()), question.getStatus(), question.getCreatedAt(),
                    question.getBody(),
                    question.getId(), question.getTitle(),
                    question.getAuthorId(), nicknameOf(question.getAuthorId()), null, null));
        }
        for (AnswerDO answer : answerDao.findByStatusOrderByIdAsc(PENDING_HUMAN)) {
            QuestionDO question = questionDao.findById(answer.getQuestionId()).orElse(null);
            if (question == null) continue;
            Long rootAnswerId = answer.getFollowupId() == null ? answer.getId()
                    : followupDao.findById(answer.getFollowupId()).map(FollowupDO::getRootAnswerId).orElse(null);
            items.add(new QueueItem("answer", answer.getId(),
                    question == null ? null : question.getTitle(),
                    snippet(answer.getBody()), answer.getStatus(), answer.getCreatedAt(),
                    answer.getBody(),
                    answer.getQuestionId(), question == null ? null : question.getTitle(),
                    answer.getAuthorId(), nicknameOf(answer.getAuthorId()), rootAnswerId, answer.getFollowupId()));
        }
        for (FollowupDO followup : followupDao.findPending()) {
            QuestionDO question = questionDao.findById(followup.getQuestionId()).orElse(null);
            if (question == null) continue;
            items.add(new QueueItem("followup", followup.getId(),
                    question == null ? null : question.getTitle(),
                    snippet(followup.getBody()), followup.getStatus(), followup.getCreatedAt(),
                    followup.getBody(),
                    followup.getQuestionId(), question == null ? null : question.getTitle(),
                    followup.getSenderId(), nicknameOf(followup.getSenderId()), followup.getRootAnswerId(), followup.getId()));
        }

        items.sort(Comparator.comparing(QueueItem::createdAt));
        return items;
    }

    @Override
    @Transactional
    public void decide(AuthUser operator, String targetType, Long targetId, DecideRequest request) {
        String normalized = request.decision().trim().toLowerCase();
        if (!DECISIONS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "decision 只能是 approve/reject/remove");
        }
        String status = switch (normalized) {
            case "approve" -> "published";
            case "reject" -> "rejected";
            default -> "removed";
        };
        boolean approved = "published".equals(status);

        switch (targetType) {
            case "question" -> {
                QuestionDO question = questionDao.findByIdForUpdate(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
                requirePending(question.getStatus(), status);
                questionDao.updateStatus(targetId, status);
                studentNotifications.contentReviewed("question", targetId, normalized);
            }
            case "answer" -> {
                if ("removed".equals(status)) {
                    answerRemovalService.removeByOperator(operator, targetId);
                } else {
                    AnswerDO answer = answerRemovalService.lockAnswer(targetId);
                    requirePending(answer.getStatus(), status);
                    answerDao.updateStatus(targetId, status);
                    if (approved) {
                        questionDao.recordAnswer(answer.getQuestionId());
                        studentNotifications.answerPublished(targetId);
                    }
                    notifications.answerReviewed(targetId, normalized);
                }
            }
            case "followup" -> {
                FollowupDO reference = followupDao.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "追问不存在"));
                questionDao.findByIdForUpdate(reference.getQuestionId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
                FollowupDO followup = followupDao.findByIdForUpdate(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "追问不存在"));
                requirePending(followup.getStatus(), status);
                followupDao.updateStatus(targetId, status);
                if (approved) questionDao.incrementFollowup(followup.getQuestionId());
                else if ("published".equals(followup.getStatus())) questionDao.decrementFollowup(followup.getQuestionId());
                if (approved) notifications.followupPublished(targetId);
                studentNotifications.contentReviewed("followup", targetId, normalized);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_PARAM, "targetType 不合法");
        }
        moderationService.recordHuman(targetType, targetId, normalized, operator.getId(), request.note());
    }



    private void requirePending(String current, String next) {
        if (!"removed".equals(next) && !PENDING_HUMAN.equals(current)) {
            throw new BusinessException(ErrorCode.CONFLICT, "此内容不在待审核状态");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportView> reports(String status) {
        return reportDao.findByStatusOrderByIdAsc(status == null || status.isBlank() ? "pending" : status)
                .stream().filter(report -> Set.of("question", "answer").contains(report.getTargetType()))
                .map(this::toReportView).toList();
    }

    @Override
    @Transactional
    public void handleReport(AuthUser operator, Long reportId, String decision, String note) {
        ReportDO report = reportDao.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "举报不存在"));
        if (!"pending".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该举报已处理");
        }
        String normalized = decision.trim().toLowerCase();
        if (!Set.of("dismiss", "remove").contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "decision 只能是 dismiss/remove");
        }
        if ("remove".equals(normalized)) {
            removeReportedTarget(operator, report.getTargetType(), report.getTargetId());
            moderationService.recordHuman(report.getTargetType(), report.getTargetId(), "remove",
                    operator.getId(), note);
        }
        report.setStatus("dismiss".equals(normalized) ? "dismissed" : "resolved");
        report.setHandledBy(operator.getId());
        report.setHandledAt(LocalDateTime.now());
        reportDao.updateResolution(report);
    }

    private void removeReportedTarget(AuthUser operator, String targetType, Long targetId) {
        switch (targetType) {
            case "question" -> {
                QuestionDO question = questionDao.findByIdForUpdate(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
                if (!"removed".equals(question.getStatus())) {
                    questionDao.updateStatus(targetId, "removed");
                    studentNotifications.contentReviewed("question", targetId, "remove");
                }
            }
            case "answer" -> answerRemovalService.removeByOperator(operator, targetId);
            default -> throw new BusinessException(ErrorCode.INVALID_PARAM, "targetType 不合法");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<WordView> words() {
        return wordDao.findAll().stream().map(this::toWordView).toList();
    }

    @Override
    @Transactional
    public WordView upsertWord(WordRequest request) {
        String word = request.word().trim();
        String category = request.category() == null ? "custom" : request.category();
        String action = "block".equals(request.action()) ? "block" : "review";
        wordDao.upsert(word, category, action);
        return toWordView(wordDao.findByWord(word));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> metrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionsPendingHuman", questionDao.countByStatus(PENDING_HUMAN));
        result.put("questionsPublished", questionDao.countByStatus("published"));
        result.put("answersPendingHuman", answerDao.countByStatus(PENDING_HUMAN));
        result.put("answersPublished", answerDao.countByStatus("published"));
        result.put("reportsPending", reportDao.countByStatus("pending"));
        return result;
    }

    private ReportView toReportView(ReportDO report) {
        String targetTitle = null;
        String targetExcerpt = null;
        switch (report.getTargetType()) {
            case "question" -> {
                QuestionDO question = questionDao.findById(report.getTargetId()).orElse(null);
                if (question != null) {
                    targetTitle = question.getTitle();
                    targetExcerpt = snippet(question.getBody());
                }
            }
            case "answer" -> {
                AnswerDO answer = answerDao.findById(report.getTargetId()).orElse(null);
                if (answer != null) {
                    targetExcerpt = snippet(answer.getBody());
                    targetTitle = questionDao.findById(answer.getQuestionId())
                            .map(QuestionDO::getTitle).orElse(null);
                }
            }

            default -> {
            }
        }
        return new ReportView(report.getId(), report.getReporterId(), report.getTargetType(), report.getTargetId(),
                report.getReason(), report.getDetail(), report.getStatus(),
                report.getHandledBy(), report.getHandledAt(), report.getCreatedAt(),
                targetTitle, targetExcerpt);
    }

    private String nicknameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userDao.findById(userId).map(UserDO::getNickname).orElse(null);
    }

    private WordView toWordView(ModerationWordDO word) {
        return new WordView(word.getId(), word.getWord(), word.getCategory(), word.getAction(), word.getUpdatedAt());
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }
}
