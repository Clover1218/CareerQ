package com.careerq.service.impl;

import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import com.careerq.dao.AnswerDao;
import com.careerq.dao.FollowupDao;
import com.careerq.dao.ModerationRecordDao;
import com.careerq.dao.ModerationWordDao;
import com.careerq.dao.QuestionDao;
import com.careerq.dao.ReportDao;
import com.careerq.dao.UserDao;
import com.careerq.dataobject.AnswerDO;
import com.careerq.dataobject.FollowupDO;
import com.careerq.dataobject.ModerationRecordDO;
import com.careerq.dataobject.ModerationWordDO;
import com.careerq.dataobject.QuestionDO;
import com.careerq.dataobject.ReportDO;
import com.careerq.dataobject.UserDO;
import com.careerq.moderation.DisplayStatus;
import com.careerq.security.AuthUser;
import com.careerq.service.AdminService;
import com.careerq.service.AdminService.BatchDecideRequest;
import com.careerq.service.AdminService.ContextPage;
import com.careerq.service.AdminService.DecideRequest;
import com.careerq.service.AdminService.RecordView;
import com.careerq.service.AdminService.ReportView;
import com.careerq.service.AdminService.ReviewDetail;
import com.careerq.service.AdminService.ReviewEntry;
import com.careerq.service.AdminService.ReviewItem;
import com.careerq.service.AdminService.ReviewPage;
import com.careerq.service.AdminService.TargetRef;
import com.careerq.service.AdminService.UndoRequest;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdminServiceImpl implements AdminService {

    private static final Set<String> TARGET_TYPES = Set.of("question", "answer", "followup");
    private static final Set<String> DECISIONS = Set.of("approve", "reject", "remove", "restore");
    /** 人工裁定只能从 L3 发起；remove/restore 走可见态，不受此限。 */
    private static final List<String> REVIEW_STATUSES = List.of("L3");
    private static final Set<String> RISK_LEVELS = Set.of("high", "watch", "normal", "unrated");
    private static final Set<String> DISPLAY_STATUSES =
            Set.of("REVIEWING", "ARBITRATING", "PUBLISHED", "REJECTED", "REMOVED", "WITHDRAWN");
    private static final int ARBITRATION_THRESHOLD = 2;
    private static final String PERMISSION_ARBITRATE = "arbitrate";
    private static final String STAGE_HUMAN = "human";
    private static final String STAGE_ARBITRATE = "ARBITRATE";

    private final QuestionDao questionDao;
    private final AnswerDao answerDao;
    private final FollowupDao followupDao;
    private final ReportDao reportDao;
    private final ModerationWordDao wordDao;
    private final ModerationRecordDao recordDao;
    private final ModerationService moderationService;
    private final UserDao userDao;

    public AdminServiceImpl(QuestionDao questionDao,
                            AnswerDao answerDao,
                            FollowupDao followupDao,
                            ReportDao reportDao,
                            ModerationWordDao wordDao,
                            ModerationRecordDao recordDao,
                            ModerationService moderationService,
                            UserDao userDao) {
        this.questionDao = questionDao;
        this.answerDao = answerDao;
        this.followupDao = followupDao;
        this.reportDao = reportDao;
        this.wordDao = wordDao;
        this.recordDao = recordDao;
        this.moderationService = moderationService;
        this.userDao = userDao;
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewPage reviewQueue(AuthUser operator, String type, String risk, String arbitration,
                                  int page, int size) {
        String typeFilter = normalizeTypeFilter(type);
        String riskFilter = normalizeRiskFilter(risk);
        Boolean arbitrationFilter = normalizeArbitrationFilter(arbitration);
        boolean canArbitrate = operator.hasPermission(PERMISSION_ARBITRATE);
        if (Boolean.TRUE.equals(arbitrationFilter) && !canArbitrate) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无仲裁权限");
        }
        int pageNo = Math.max(page, 1);
        int pageSize = Math.clamp(size, 1, 50);

        List<ReviewItem> items = new ArrayList<>();
        if (typeFilter == null || "question".equals(typeFilter)) {
            for (QuestionDO question : questionDao.findPending()) {
                items.add(toQuestionItem(question));
            }
        }
        if (typeFilter == null || "answer".equals(typeFilter)) {
            for (AnswerDO answer : answerDao.findPending()) {
                QuestionDO question = questionDao.findById(answer.getQuestionId()).orElse(null);
                if (question != null) {
                    items.add(toAnswerItem(answer, question));
                }
            }
        }
        if (typeFilter == null || "followup".equals(typeFilter)) {
            for (FollowupDO followup : followupDao.findPending()) {
                QuestionDO question = questionDao.findById(followup.getQuestionId()).orElse(null);
                if (question != null) {
                    items.add(toFollowupItem(followup, question));
                }
            }
        }
        // 非仲裁员看不到待仲裁单子，避免点进去才 403
        items.removeIf(item -> !canArbitrate && item.arbitrationRequired());
        if (arbitrationFilter != null) {
            items.removeIf(item -> item.arbitrationRequired() != arbitrationFilter);
        }
        items.removeIf(item -> riskFilter != null && !riskFilter.equalsIgnoreCase(item.riskLevel()));
        items.sort(Comparator.comparing((ReviewItem item) -> !item.arbitrationRequired())
                .thenComparingInt(item -> riskRank(item.riskLevel()))
                .thenComparing(ReviewItem::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ReviewItem::targetId)
                // targetId 在三张内容表中会重复，补 targetType 才能保证分页顺序唯一
                .thenComparing(ReviewItem::targetType));
        return slice(items, pageNo, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewPage reviewed(AuthUser operator, String type, String status, String risk, int page, int size) {
        String typeFilter = normalizeTypeFilter(type);
        String statusFilter = normalizeStatusFilter(status);
        String riskFilter = normalizeRiskFilter(risk);
        int pageNo = Math.max(page, 1);
        int pageSize = Math.clamp(size, 1, 50);

        List<ReviewItem> items = new ArrayList<>();
        for (ModerationRecordDO record : recordDao.findReviewedByOperator(operator.getId(), 0, 500)) {
            if (!TARGET_TYPES.contains(record.getTargetType())) {
                continue;
            }
            ReviewItem item = reviewedItem(record);
            if (item == null) {
                continue;
            }
            if (typeFilter != null && !typeFilter.equals(record.getTargetType())) {
                continue;
            }
            if (statusFilter != null && !statusFilter.equals(item.displayStatus())) {
                continue;
            }
            if (riskFilter != null && !riskFilter.equalsIgnoreCase(item.riskLevel())) {
                continue;
            }
            items.add(item);
        }
        return slice(items, pageNo, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewDetail detail(String targetType, Long targetId) {
        String type = normalizeTargetType(targetType);
        ReviewItem item = reviewItem(type, targetId);
        List<ReviewEntry> context = context(item.questionId(), type, targetId);
        int targetIndex = targetIndex(context);
        List<ReviewEntry> window;
        int beforeRemaining;
        int afterRemaining;
        if (targetIndex < 0) {
            window = List.of();
            beforeRemaining = 0;
            afterRemaining = 0;
        } else {
            int from = Math.max(targetIndex - 1, 0);
            int to = Math.min(targetIndex + 1, context.size() - 1);
            window = List.copyOf(context.subList(from, to + 1));
            beforeRemaining = from;
            afterRemaining = context.size() - 1 - to;
        }
        List<RecordView> records = recordDao.findByTarget(type, targetId).stream()
                .map(this::toRecordView).toList();
        return new ReviewDetail(item, window, beforeRemaining, afterRemaining, records);
    }

    @Override
    @Transactional(readOnly = true)
    public ContextPage contextBefore(String targetType, Long targetId, String cursor, int limit) {
        return contextSlice(targetType, targetId, cursor, limit, true);
    }

    @Override
    @Transactional(readOnly = true)
    public ContextPage contextAfter(String targetType, Long targetId, String cursor, int limit) {
        return contextSlice(targetType, targetId, cursor, limit, false);
    }

    private ContextPage contextSlice(String targetType, Long targetId, String cursor, int limit, boolean before) {
        String type = normalizeTargetType(targetType);
        ReviewItem item = reviewItem(type, targetId);
        List<ReviewEntry> context = context(item.questionId(), type, targetId);
        int cursorIndex = cursorIndex(context, cursor);
        int size = Math.max(1, Math.min(limit, 10));
        if (before) {
            int from = Math.max(cursorIndex - size, 0);
            return new ContextPage(List.copyOf(context.subList(from, cursorIndex)), from);
        }
        int to = Math.min(cursorIndex + size + 1, context.size());
        return new ContextPage(List.copyOf(context.subList(cursorIndex + 1, to)), context.size() - to);
    }

    private ReviewItem reviewItem(String type, Long targetId) {
        return switch (type) {
            case "question" -> questionDao.findById(targetId)
                    .map(this::toQuestionItem)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
            case "answer" -> {
                AnswerDO answer = answerDao.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回答不存在"));
                QuestionDO question = questionDao.findById(answer.getQuestionId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
                yield toAnswerItem(answer, question);
            }
            default -> {
                FollowupDO followup = followupDao.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "追问不存在"));
                QuestionDO question = questionDao.findById(followup.getQuestionId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
                yield toFollowupItem(followup, question);
            }
        };
    }

    private int targetIndex(List<ReviewEntry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).target()) {
                return i;
            }
        }
        return -1;
    }

    private int cursorIndex(List<ReviewEntry> entries, String cursor) {
        String value = cursor == null ? "" : cursor.trim();
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "cursor 格式应为 kind:id");
        }
        String kind = value.substring(0, colon);
        long id;
        try {
            id = Long.parseLong(value.substring(colon + 1));
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "cursor 格式应为 kind:id");
        }
        for (int i = 0; i < entries.size(); i++) {
            ReviewEntry entry = entries.get(i);
            if (entry.kind().equals(kind) && entry.id() == id) {
                return i;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_PARAM, "cursor 对应的上下文不存在");
    }

    @Override
    @Transactional
    public void decide(AuthUser operator, String targetType, Long targetId, DecideRequest request) {
        applyDecision(operator, normalizeTargetType(targetType), targetId,
                normalizeDecision(request.decision()), request.note());
    }

    @Override
    @Transactional
    public void undo(AuthUser operator, String targetType, Long targetId, UndoRequest request) {
        String type = normalizeTargetType(targetType);
        String note = request == null ? null : request.note();
        switch (type) {
            case "question" -> undoQuestion(targetId, operator, note);
            case "answer" -> undoAnswer(targetId, operator, note);
            default -> undoFollowup(targetId, operator, note);
        }
    }

    @Override
    @Transactional
    public void batchDecide(AuthUser operator, BatchDecideRequest request) {
        if (request.targets() == null || request.targets().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "请选择要审核的内容");
        }
        String decision = normalizeDecision(request.decision());
        for (TargetRef target : request.targets()) {
            applyDecision(operator, normalizeTargetType(target.targetType()), target.targetId(),
                    decision, request.note());
        }
    }

    private void applyDecision(AuthUser operator, String targetType, Long targetId,
                               String decision, String note) {
        switch (targetType) {
            case "question" -> {
                QuestionDO question = questionDao.findByIdForUpdate(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
                applyQuestion(question, operator, decision, note);
            }
            case "answer" -> {
                AnswerDO answer = answerDao.findByIdForUpdate(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回答不存在"));
                applyAnswer(answer, operator, decision, note);
            }
            default -> {
                FollowupDO followup = followupDao.findByIdForUpdate(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "追问不存在"));
                applyFollowup(followup, operator, decision, note);
            }
        }
    }

    private void applyQuestion(QuestionDO question, AuthUser operator, String decision, String note) {
        String fromStatus = question.getModerationStatus();
        String risk = question.getRiskLevel();
        if ("remove".equals(decision) || "restore".equals(decision)) {
            int updated = "remove".equals(decision)
                    ? questionDao.updateVisibilityIf(question.getId(), List.of("APPROVED"),
                    List.of("VISIBLE"), "REMOVED", note)
                    : questionDao.updateVisibilityIf(question.getId(), List.of("APPROVED"),
                    List.of("REMOVED"), "VISIBLE", note);
            requireUpdated(updated);
            moderationService.recordHuman("question", question.getId(), decision,
                    fromStatus, "APPROVED", risk, operator.getId(), note);
            return;
        }
        boolean arbitration = requireAdjudication(operator, "question", question.getId(),
                fromStatus, question.getReviewAttempts());
        String toStatus = "approve".equals(decision) ? "APPROVED" : "REJECTED";
        String visibility = "approve".equals(decision) ? "VISIBLE" : "HIDDEN";
        int updated = questionDao.updateModerationIfStatusIn(question.getId(), REVIEW_STATUSES,
                toStatus, visibility, risk, note);
        requireUpdated(updated);
        recordAdjudication("question", question.getId(), arbitration, decision,
                fromStatus, toStatus, risk, operator, note);
    }

    private void applyAnswer(AnswerDO answer, AuthUser operator, String decision, String note) {
        String fromStatus = answer.getModerationStatus();
        String fromVisibility = answer.getVisibility();
        String risk = answer.getRiskLevel();
        if ("remove".equals(decision) || "restore".equals(decision)) {
            int updated = "remove".equals(decision)
                    ? answerDao.updateVisibilityIf(answer.getId(), List.of("APPROVED"),
                    List.of("VISIBLE"), "REMOVED", note)
                    : answerDao.updateVisibilityIf(answer.getId(), List.of("APPROVED"),
                    List.of("REMOVED"), "VISIBLE", note);
            requireUpdated(updated);
            if ("remove".equals(decision)) {
                questionDao.decrementAnswer(answer.getQuestionId());
            } else {
                questionDao.recordAnswer(answer.getQuestionId());
            }
            moderationService.recordHuman("answer", answer.getId(), decision,
                    fromStatus, "APPROVED", risk, operator.getId(), note);
            return;
        }
        boolean arbitration = requireAdjudication(operator, "answer", answer.getId(),
                fromStatus, answer.getReviewAttempts());
        String toStatus = "approve".equals(decision) ? "APPROVED" : "REJECTED";
        String visibility = "approve".equals(decision) ? "VISIBLE" : "HIDDEN";
        int updated = answerDao.updateModerationIfStatusIn(answer.getId(), REVIEW_STATUSES,
                toStatus, visibility, risk, note);
        requireUpdated(updated);
        if ("approve".equals(decision) && !"VISIBLE".equals(fromVisibility)) {
            questionDao.recordAnswer(answer.getQuestionId());
        } else if ("reject".equals(decision) && "VISIBLE".equals(fromVisibility)) {
            questionDao.decrementAnswer(answer.getQuestionId());
        }
        recordAdjudication("answer", answer.getId(), arbitration, decision,
                fromStatus, toStatus, risk, operator, note);
    }

    private void applyFollowup(FollowupDO followup, AuthUser operator, String decision, String note) {
        String fromStatus = followup.getModerationStatus();
        String fromVisibility = followup.getVisibility();
        String risk = followup.getRiskLevel();
        if ("remove".equals(decision) || "restore".equals(decision)) {
            int updated = "remove".equals(decision)
                    ? followupDao.updateVisibilityIf(followup.getId(), List.of("APPROVED"),
                    List.of("VISIBLE"), "REMOVED", note)
                    : followupDao.updateVisibilityIf(followup.getId(), List.of("APPROVED"),
                    List.of("REMOVED"), "VISIBLE", note);
            requireUpdated(updated);
            if ("remove".equals(decision)) {
                questionDao.decrementFollowup(followup.getQuestionId());
            } else {
                questionDao.incrementFollowup(followup.getQuestionId());
            }
            moderationService.recordHuman("followup", followup.getId(), decision,
                    fromStatus, "APPROVED", risk, operator.getId(), note);
            return;
        }
        boolean arbitration = requireAdjudication(operator, "followup", followup.getId(),
                fromStatus, followup.getReviewAttempts());
        String toStatus = "approve".equals(decision) ? "APPROVED" : "REJECTED";
        String visibility = "approve".equals(decision) ? "VISIBLE" : "HIDDEN";
        int updated = followupDao.updateModerationIfStatusIn(followup.getId(), REVIEW_STATUSES,
                toStatus, visibility, risk, note);
        requireUpdated(updated);
        if ("approve".equals(decision) && !"VISIBLE".equals(fromVisibility)) {
            questionDao.incrementFollowup(followup.getQuestionId());
        } else if ("reject".equals(decision) && "VISIBLE".equals(fromVisibility)) {
            questionDao.decrementFollowup(followup.getQuestionId());
        }
        recordAdjudication("followup", followup.getId(), arbitration, decision,
                fromStatus, toStatus, risk, operator, note);
    }

    private boolean requireAdjudication(AuthUser operator, String targetType, Long targetId,
                                        String currentStatus, int reviewAttempts) {
        if (!"L3".equals(currentStatus)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该内容当前不可审核");
        }
        if (recordDao.hasDecided(operator.getId(), targetType, targetId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能重复审核同一内容");
        }
        boolean canArbitrate = operator.hasPermission(PERMISSION_ARBITRATE);
        if (reviewAttempts >= ARBITRATION_THRESHOLD && !canArbitrate) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无仲裁权限");
        }
        // 仲裁权限者的任何裁定都按仲裁处理：终局，不可撤销
        return canArbitrate;
    }

    private void recordAdjudication(String targetType, Long targetId, boolean arbitration,
                                    String decision, String fromStatus, String toStatus,
                                    String risk, AuthUser operator, String note) {
        moderationService.recordManual(targetType, targetId,
                arbitration ? STAGE_ARBITRATE : STAGE_HUMAN,
                decision, fromStatus, toStatus, risk, operator.getId(), note);
    }

    private void undoQuestion(Long targetId, AuthUser operator, String note) {
        QuestionDO question = questionDao.findByIdForUpdate(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        requireUndoable("question", targetId, operator, question.getModerationStatus());
        requireUpdated(questionDao.undoModerationIf(targetId, note));
        moderationService.recordManual("question", targetId, STAGE_HUMAN, "undo",
                question.getModerationStatus(), "L3", question.getRiskLevel(), operator.getId(), note);
    }

    private void undoAnswer(Long targetId, AuthUser operator, String note) {
        AnswerDO answer = answerDao.findByIdForUpdate(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回答不存在"));
        requireUndoable("answer", targetId, operator, answer.getModerationStatus());
        boolean wasVisible = "VISIBLE".equals(answer.getVisibility());
        requireUpdated(answerDao.undoModerationIf(targetId, note));
        if (wasVisible) {
            questionDao.decrementAnswer(answer.getQuestionId());
        }
        moderationService.recordManual("answer", targetId, STAGE_HUMAN, "undo",
                answer.getModerationStatus(), "L3", answer.getRiskLevel(), operator.getId(), note);
    }

    private void undoFollowup(Long targetId, AuthUser operator, String note) {
        FollowupDO followup = followupDao.findByIdForUpdate(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "追问不存在"));
        requireUndoable("followup", targetId, operator, followup.getModerationStatus());
        boolean wasVisible = "VISIBLE".equals(followup.getVisibility());
        requireUpdated(followupDao.undoModerationIf(targetId, note));
        if (wasVisible) {
            questionDao.decrementFollowup(followup.getQuestionId());
        }
        moderationService.recordManual("followup", targetId, STAGE_HUMAN, "undo",
                followup.getModerationStatus(), "L3", followup.getRiskLevel(), operator.getId(), note);
    }

    private void requireUndoable(String targetType, Long targetId, AuthUser operator, String currentStatus) {
        if (!"APPROVED".equals(currentStatus) && !"REJECTED".equals(currentStatus)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该内容当前不可撤销");
        }
        ModerationRecordDO latest = recordDao.findLatestAdjudication(targetType, targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "无权撤销该审核"));
        if (!operator.getId().equals(latest.getOperatorId()) || !STAGE_HUMAN.equals(latest.getStage())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权撤销该审核");
        }
    }

    private void requireUpdated(int updated) {
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该内容已被其他审核员处理，请刷新后重试");
        }
    }

    private ReviewItem reviewedItem(ModerationRecordDO record) {
        try {
            return switch (record.getTargetType()) {
                case "question" -> questionDao.findById(record.getTargetId())
                        .map(question -> withReview(toQuestionItem(question), record))
                        .orElse(null);
                case "answer" -> answerDao.findById(record.getTargetId())
                        .flatMap(answer -> questionDao.findById(answer.getQuestionId())
                                .map(question -> withReview(toAnswerItem(answer, question), record)))
                        .orElse(null);
                case "followup" -> followupDao.findById(record.getTargetId())
                        .flatMap(followup -> questionDao.findById(followup.getQuestionId())
                                .map(question -> withReview(toFollowupItem(followup, question), record)))
                        .orElse(null);
                default -> null;
            };
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private ReviewItem withReview(ReviewItem item, ModerationRecordDO record) {
        return new ReviewItem(item.targetType(), item.targetId(), item.title(), item.snippet(), item.body(),
                item.riskLevel(), item.displayStatus(), item.authorId(), item.authorNickname(),
                item.questionId(), item.questionTitle(), item.createdAt(),
                record.getCreatedAt(), record.getDecision(),
                item.reviewAttempts(), item.arbitrationRequired(), canUndo(item, record));
    }

    private boolean canUndo(ReviewItem item, ModerationRecordDO record) {
        if (!STAGE_HUMAN.equals(record.getStage())
                || !("approve".equals(record.getDecision()) || "reject".equals(record.getDecision()))
                || !("PUBLISHED".equals(item.displayStatus()) || "REJECTED".equals(item.displayStatus()))) {
            return false;
        }
        return recordDao.findLatestAdjudication(record.getTargetType(), record.getTargetId())
                .map(latest -> latest.getId().equals(record.getId()))
                .orElse(false);
    }

    private ReviewItem toQuestionItem(QuestionDO question) {
        int attempts = question.getReviewAttempts();
        return new ReviewItem("question", question.getId(), question.getTitle(), snippet(question.getBody()),
                question.getBody(), riskOf(question.getRiskLevel()),
                displayStatus(question.getModerationStatus(), question.getVisibility(), attempts),
                question.getAuthorId(), nicknameOf(question.getAuthorId()),
                question.getId(), question.getTitle(), question.getCreatedAt(), null, null,
                attempts, attempts >= ARBITRATION_THRESHOLD, false);
    }

    private ReviewItem toAnswerItem(AnswerDO answer, QuestionDO question) {
        int attempts = answer.getReviewAttempts();
        return new ReviewItem("answer", answer.getId(), question.getTitle(), snippet(answer.getBody()),
                answer.getBody(), riskOf(answer.getRiskLevel()),
                displayStatus(answer.getModerationStatus(), answer.getVisibility(), attempts),
                answer.getAuthorId(), nicknameOf(answer.getAuthorId()),
                question.getId(), question.getTitle(), answer.getCreatedAt(), null, null,
                attempts, attempts >= ARBITRATION_THRESHOLD, false);
    }

    private ReviewItem toFollowupItem(FollowupDO followup, QuestionDO question) {
        int attempts = followup.getReviewAttempts();
        return new ReviewItem("followup", followup.getId(), question.getTitle(), snippet(followup.getBody()),
                followup.getBody(), riskOf(followup.getRiskLevel()),
                displayStatus(followup.getModerationStatus(), followup.getVisibility(), attempts),
                followup.getSenderId(), nicknameOf(followup.getSenderId()),
                question.getId(), question.getTitle(), followup.getCreatedAt(), null, null,
                attempts, attempts >= ARBITRATION_THRESHOLD, false);
    }

    private String displayStatus(String moderationStatus, String visibility, int reviewAttempts) {
        return DisplayStatus.of(moderationStatus, visibility, reviewAttempts).name();
    }

    private List<ReviewEntry> context(Long questionId, String targetType, Long targetId) {
        QuestionDO question = questionDao.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "问题不存在"));
        List<ReviewEntry> entries = new ArrayList<>();
        entries.add(new ReviewEntry("question", question.getId(), nicknameOf(question.getAuthorId()),
                "student", question.getBody(),
                DisplayStatus.of(question.getModerationStatus(), question.getVisibility()).name(),
                "question".equals(targetType) && question.getId().equals(targetId),
                question.getCreatedAt()));
        for (AnswerDO answer : answerDao.findVisible(questionId, null, true)) {
            entries.add(new ReviewEntry(
                    answer.getFollowupId() == null ? "answer" : "followup_answer",
                    answer.getId(), nicknameOf(answer.getAuthorId()), "hr", answer.getBody(),
                    DisplayStatus.of(answer.getModerationStatus(), answer.getVisibility()).name(),
                    "answer".equals(targetType) && answer.getId().equals(targetId),
                    answer.getCreatedAt()));
        }
        for (FollowupDO followup : followupDao.findVisible(questionId, null, true)) {
            entries.add(new ReviewEntry("followup", followup.getId(), nicknameOf(followup.getSenderId()),
                    followup.getSenderRole(), followup.getBody(),
                    DisplayStatus.of(followup.getModerationStatus(), followup.getVisibility()).name(),
                    "followup".equals(targetType) && followup.getId().equals(targetId),
                    followup.getCreatedAt()));
        }
        entries.sort(Comparator.comparing(ReviewEntry::createdAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(entry -> "question".equals(entry.kind()) ? 0 : 1)
                .thenComparing(ReviewEntry::id));
        return entries;
    }

    private RecordView toRecordView(ModerationRecordDO record) {
        return new RecordView(record.getId(), record.getStage(), record.getDecision(),
                record.getRiskLevel(), record.getRiskLabels(), record.getConfidence(), record.getNote(),
                nicknameOf(record.getOperatorId()), record.getFromModerationStatus(),
                record.getToModerationStatus(), record.getCreatedAt());
    }

    private ReviewPage slice(List<ReviewItem> items, int page, int size) {
        int from = Math.min((page - 1) * size, items.size());
        int to = Math.min(from + size, items.size());
        return new ReviewPage(items.subList(from, to), items.size(), page, size);
    }

    private int riskRank(String risk) {
        return switch (risk == null ? "UNRATED" : risk.toUpperCase(Locale.ROOT)) {
            case "HIGH" -> 0;
            case "WATCH" -> 1;
            case "NORMAL" -> 2;
            default -> 3;
        };
    }

    private String riskOf(String risk) {
        return risk == null || risk.isBlank() ? "UNRATED" : risk;
    }

    private String normalizeTargetType(String targetType) {
        String value = targetType == null ? "" : targetType.trim().toLowerCase(Locale.ROOT);
        if (!TARGET_TYPES.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "targetType 只能是 question/answer/followup");
        }
        return value;
    }

    private String normalizeTypeFilter(String type) {
        if (type == null || type.isBlank() || "all".equalsIgnoreCase(type)) {
            return null;
        }
        return normalizeTargetType(type);
    }

    private String normalizeRiskFilter(String risk) {
        if (risk == null || risk.isBlank() || "all".equalsIgnoreCase(risk)) {
            return null;
        }
        String value = risk.trim().toLowerCase(Locale.ROOT);
        if (!RISK_LEVELS.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "risk 只能是 high/watch/normal/unrated");
        }
        return value;
    }

    private Boolean normalizeArbitrationFilter(String arbitration) {
        if (arbitration == null || arbitration.isBlank()) {
            return null;
        }
        String value = arbitration.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new BusinessException(ErrorCode.INVALID_PARAM, "arbitration 只能是 true/false");
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return null;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!DISPLAY_STATUSES.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM,
                    "status 只能是 REVIEWING/ARBITRATING/PUBLISHED/REJECTED/REMOVED/WITHDRAWN");
        }
        return value;
    }

    private String normalizeDecision(String decision) {
        String value = decision == null ? "" : decision.trim().toLowerCase(Locale.ROOT);
        if (!DECISIONS.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM,
                    "decision 只能是 approve/reject/remove/restore");
        }
        return value;
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
        String normalized = decision.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("dismiss", "remove").contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAM, "decision 只能是 dismiss/remove");
        }
        if ("remove".equals(normalized)) {
            applyDecision(operator, normalizeTargetType(report.getTargetType()),
                    report.getTargetId(), "remove", note);
        }
        report.setStatus("dismiss".equals(normalized) ? "dismissed" : "resolved");
        report.setHandledBy(operator.getId());
        report.setHandledAt(LocalDateTime.now());
        reportDao.updateResolution(report);
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
        long questionArbitration = questionDao.countArbitration();
        long answerArbitration = answerDao.countArbitration();
        long followupArbitration = followupDao.countArbitration();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionsReviewing", questionDao.countByModerationStatus("L3") - questionArbitration);
        result.put("answersReviewing", answerDao.countByModerationStatus("L3") - answerArbitration);
        result.put("followupsReviewing", followupDao.countByModerationStatus("L3") - followupArbitration);
        result.put("arbitrationPending", questionArbitration + answerArbitration + followupArbitration);
        result.put("questionsPublished", questionDao.countByModerationStatusAndVisibility("APPROVED", "VISIBLE"));
        result.put("answersPublished", answerDao.countByModerationStatusAndVisibility("APPROVED", "VISIBLE"));
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