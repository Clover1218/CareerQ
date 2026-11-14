package com.careerq.admin;

import com.careerq.answer.Answer;
import com.careerq.answer.AnswerRepository;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.moderation.ModerationService;
import com.careerq.moderation.ModerationWord;
import com.careerq.moderation.ModerationWordRepository;
import com.careerq.publicpost.PublicPost;
import com.careerq.publicpost.PublicPostRepository;
import com.careerq.question.Question;
import com.careerq.question.QuestionRepository;
import com.careerq.report.Report;
import com.careerq.report.ReportRepository;
import com.careerq.service.AdminService.HandleReportRequest;
import com.careerq.service.AdminService.QueueItem;
import com.careerq.service.AdminService.ReportView;
import com.careerq.service.AdminService.WordRequest;
import com.careerq.service.AdminService.WordView;
import com.careerq.security.AuthUser;
import jakarta.validation.constraints.NotBlank;
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
public class AdminService implements com.careerq.service.AdminService {

    private static final String PENDING_HUMAN = "pending_human";
    private static final Set<String> DECISIONS = Set.of("approve", "reject", "remove");

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final PublicPostRepository publicPostRepository;
    private final ReportRepository reportRepository;
    private final ModerationWordRepository wordRepository;
    private final ModerationService moderationService;

    public AdminService(QuestionRepository questionRepository,
                        AnswerRepository answerRepository,
                        PublicPostRepository publicPostRepository,
                        ReportRepository reportRepository,
                        ModerationWordRepository wordRepository,
                        ModerationService moderationService) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.publicPostRepository = publicPostRepository;
        this.reportRepository = reportRepository;
        this.wordRepository = wordRepository;
        this.moderationService = moderationService;
    }


    @Transactional(readOnly = true)
    public List<QueueItem> queue() {
        List<QueueItem> items = new ArrayList<>();
        for (Question question : questionRepository.findByStatusOrderByIdAsc(PENDING_HUMAN)) {
            items.add(new QueueItem("question", question.getId(), question.getTitle(),
                    snippet(question.getBody()), question.getStatus(), question.getCreatedAt()));
        }
        for (Answer answer : answerRepository.findByStatusOrderByIdAsc(PENDING_HUMAN)) {
            items.add(new QueueItem("answer", answer.getId(), null,
                    snippet(answer.getBody()), answer.getStatus(), answer.getCreatedAt()));
        }
        for (PublicPost post : publicPostRepository.findByStatusOrderByIdAsc(PENDING_HUMAN)) {
            items.add(new QueueItem("public_post", post.getId(), post.getTitle(),
                    snippet(post.getQuestionBody()), post.getStatus(), post.getCreatedAt()));
        }
        items.sort(Comparator.comparing(QueueItem::createdAt));
        return items;
    }

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
        switch (targetType) {
            case "question" -> {
                Question question = questionRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
                question.setStatus(status);
                questionRepository.save(question);
            }
            case "answer" -> {
                Answer answer = answerRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回答不存在"));
                answer.setStatus(status);
                answerRepository.save(answer);
            }
            case "public_post" -> {
                PublicPost post = publicPostRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "公开帖不存在"));
                post.setStatus(status);
                if ("published".equals(status)) {
                    post.setPublishedAt(LocalDateTime.now());
                }
                publicPostRepository.save(post);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_PARAM, "targetType 不合法");
        }
        moderationService.recordHuman(targetType, targetId, normalized, operator.getId(), note);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ReportView> reports(String status) {
        return reportRepository.findByStatusOrderByIdAsc(status == null || status.isBlank() ? "pending" : status)
                .stream().map(this::toReportView).toList();
    }

    @Transactional
    public void handleReport(AuthUser operator, Long reportId, String decision, String note) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "举报不存在"));
        if (!"pending".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该举报已处理");
        }
        String normalized = decision.trim().toLowerCase();
        if (!Set.of("dismiss", "remove").contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "decision 只能是 dismiss/remove");
        }
        if ("remove".equals(normalized)) {
            removeTarget(report.getTargetType(), report.getTargetId());
        }
        report.setStatus("dismiss".equals(normalized) ? "dismissed" : "resolved");
        report.setHandledBy(operator.getId());
        report.setHandledAt(LocalDateTime.now());
        reportRepository.save(report);
    }

    private void removeTarget(String targetType, Long targetId) {
        switch (targetType) {
            case "question" -> questionRepository.findById(targetId).ifPresent(question -> {
                question.setStatus("removed");
                questionRepository.save(question);
            });
            case "answer" -> answerRepository.findById(targetId).ifPresent(answer -> {
                answer.setStatus("removed");
                answerRepository.save(answer);
            });
            case "public_post" -> publicPostRepository.findById(targetId).ifPresent(post -> {
                post.setStatus("removed");
                publicPostRepository.save(post);
            });
            default -> throw new BusinessException(ErrorCode.INVALID_PARAM, "targetType 不合法");
        }
    }

    @Transactional(readOnly = true)
    @Override
    public List<WordView> words() {
        return wordRepository.findAll().stream().map(this::toWordView).toList();
    }

    @Transactional
    @Override
    public WordView upsertWord(WordRequest request) {
        String word = request.word().trim();
        ModerationWord item = wordRepository.findByWord(word).orElseGet(ModerationWord::new);
        item.setWord(word);
        item.setCategory(request.category() == null ? "custom" : request.category());
        item.setAction("block".equals(request.action()) ? "block" : "review");
        return toWordView(wordRepository.save(item));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> metrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionsPendingHuman", questionRepository.countByStatus(PENDING_HUMAN));
        result.put("questionsPublished", questionRepository.countByStatus("published"));
        result.put("answersPendingHuman", answerRepository.countByStatus(PENDING_HUMAN));
        result.put("answersPublished", answerRepository.countByStatus("published"));
        result.put("publicPostsPendingHuman", publicPostRepository.countByStatus(PENDING_HUMAN));
        result.put("publicPostsPublished", publicPostRepository.countByStatus("published"));
        result.put("reportsPending", reportRepository.countByStatus("pending"));
        return result;
    }

    private ReportView toReportView(Report report) {
        return new ReportView(report.getId(), report.getReporterId(), report.getTargetType(), report.getTargetId(),
                report.getReason(), report.getDetail(), report.getStatus(),
                report.getHandledBy(), report.getHandledAt(), report.getCreatedAt());
    }

    private WordView toWordView(ModerationWord word) {
        return new WordView(word.getId(), word.getWord(), word.getCategory(), word.getAction(), word.getUpdatedAt());
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }
}
