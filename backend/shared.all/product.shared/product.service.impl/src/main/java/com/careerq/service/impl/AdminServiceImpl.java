package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.ModerationWordDao;
import com.careerq.dao.PublicPostDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dao.ReportDao;
import com.careerq.dataobject.AnswerDO;
import com.careerq.dataobject.ModerationWordDO;
import com.careerq.dataobject.PublicPostDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.dataobject.ReportDO;
import com.careerq.security.AuthUser;
import com.careerq.service.AdminService;
import com.careerq.service.AdminService.HandleReportRequest;
import com.careerq.service.AdminService.QueueItem;
import com.careerq.service.AdminService.ReportView;
import com.careerq.service.AdminService.WordRequest;
import com.careerq.service.AdminService.WordView;
import com.careerq.service.ModerationService;
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
    private final PublicPostDao publicPostDao;
    private final ReportDao reportDao;
    private final ModerationWordDao wordDao;
    private final ModerationService moderationService;

    public AdminServiceImpl(QuestionDao questionDao,
                            AnswerDao answerDao,
                            PublicPostDao publicPostDao,
                            ReportDao reportDao,
                            ModerationWordDao wordDao,
                            ModerationService moderationService) {
        this.questionDao = questionDao;
        this.answerDao = answerDao;
        this.publicPostDao = publicPostDao;
        this.reportDao = reportDao;
        this.wordDao = wordDao;
        this.moderationService = moderationService;
    }


    @Override
    @Transactional(readOnly = true)
    public List<QueueItem> queue() {
        List<QueueItem> items = new ArrayList<>();
        for (QuestionDO question : questionDao.findByStatusOrderByIdAsc(PENDING_HUMAN)) {
            items.add(new QueueItem("question", question.getId(), question.getTitle(),
                    snippet(question.getBody()), question.getStatus(), question.getCreatedAt()));
        }
        for (AnswerDO answer : answerDao.findByStatusOrderByIdAsc(PENDING_HUMAN)) {
            items.add(new QueueItem("answer", answer.getId(), null,
                    snippet(answer.getBody()), answer.getStatus(), answer.getCreatedAt()));
        }
        for (PublicPostDO post : publicPostDao.findByStatusOrderByIdAsc(PENDING_HUMAN)) {
            items.add(new QueueItem("public_post", post.getId(), post.getTitle(),
                    snippet(post.getQuestionBody()), post.getStatus(), post.getCreatedAt()));
        }
        items.sort(Comparator.comparing(QueueItem::createdAt));
        return items;
    }

    @Override
    @Transactional
    public void decide(AuthUser operator, String targetType, Long targetId, String decision, String note) {
        String normalized = decision.trim().toLowerCase();
        if (!DECISIONS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "decision 只能是 approve/reject/remove");
        }
        String status = switch (normalized) {
            case "approve" -> "published";
            case "reject" -> "rejected";
            default -> "removed";
        };
        LocalDateTime publishedAt = "published".equals(status) ? LocalDateTime.now() : null;
        if (updateTargetStatus(targetType, targetId, status, publishedAt) == 0) {
            String message = switch (targetType) {
                case "question" -> "问题不存在";
                case "answer" -> "回答不存在";
                default -> "公开帖不存在";
            };
            throw new BusinessException(ErrorCode.NOT_FOUND, message);
        }
        moderationService.recordHuman(targetType, targetId, normalized, operator.getId(), note);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportView> reports(String status) {
        return reportDao.findByStatusOrderByIdAsc(status == null || status.isBlank() ? "pending" : status)
                .stream().map(this::toReportView).toList();
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
            updateTargetStatus(report.getTargetType(), report.getTargetId(), "removed", null);
        }
        report.setStatus("dismiss".equals(normalized) ? "dismissed" : "resolved");
        report.setHandledBy(operator.getId());
        report.setHandledAt(LocalDateTime.now());
        reportDao.updateResolution(report);
    }

    private int updateTargetStatus(String targetType, Long targetId, String status, LocalDateTime publishedAt) {
        return switch (targetType) {
            case "question" -> questionDao.updateStatus(targetId, status);
            case "answer" -> answerDao.updateStatus(targetId, status);
            case "public_post" -> publicPostDao.updateStatus(targetId, status, publishedAt);
            default -> throw new BusinessException(ErrorCode.INVALID_PARAM, "targetType 不合法");
        };
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
        result.put("publicPostsPendingHuman", publicPostDao.countByStatus(PENDING_HUMAN));
        result.put("publicPostsPublished", publicPostDao.countByStatus("published"));
        result.put("reportsPending", reportDao.countByStatus("pending"));
        return result;
    }

    private ReportView toReportView(ReportDO report) {
        return new ReportView(report.getId(), report.getReporterId(), report.getTargetType(), report.getTargetId(),
                report.getReason(), report.getDetail(), report.getStatus(),
                report.getHandledBy(), report.getHandledAt(), report.getCreatedAt());
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
