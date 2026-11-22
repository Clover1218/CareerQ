package com.careerq.service.impl;

import com.careerq.dao.ModerationRecordDao;
import com.careerq.dao.ModerationWordDao;
import com.careerq.dataobject.ModerationRecordDO;
import com.careerq.dataobject.ModerationWordDO;
import com.careerq.moderation.ModerationAiClient;
import com.careerq.moderation.ModerationAiClient.ModerationAiInput;
import com.careerq.moderation.ModerationAiClient.ModerationAiResult;
import com.careerq.moderation.ModerationPlan;
import com.careerq.moderation.ModerationPlan.L1Result;
import com.careerq.moderation.ModerationPlan.L2Result;
import com.careerq.moderation.ModerationStatus;
import com.careerq.moderation.RiskLevel;
import com.careerq.moderation.Visibility;
import com.careerq.service.ModerationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 内容裁定 + 审核留痕。
 * L1 同步执行确定性规则；L2 当前可配置为自动通过，接入 AI 后关闭该开关。
 */
@Service
public class ModerationServiceImpl implements ModerationService {

    private static final List<String> REVIEW_WORDS = List.of(
            "代写", "刷单", "加微信", "加QQ", "联系方式", "包过", "内推收费",
            "http://", "https://");

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern URL = Pattern.compile("(https?://|www\\.)\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTACT = Pattern.compile("(微信|vx|v信|加q|qq\\d{5,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATED = Pattern.compile("(.)\\1{5,}");

    private static final String STAGE_L1 = "L1";
    private static final String STAGE_L2 = "L2";
    private static final String STAGE_HUMAN = "human";

    private final ModerationWordDao wordDao;
    private final ModerationRecordDao recordDao;
    private final ModerationAiClient aiClient;
    private final boolean l2AutoPass;

    public ModerationServiceImpl(ModerationWordDao wordDao,
                                 ModerationRecordDao recordDao,
                                 ModerationAiClient aiClient,
                                 @Value("${careerq.moderation.l2-auto-pass:true}") boolean l2AutoPass) {
        this.wordDao = wordDao;
        this.recordDao = recordDao;
        this.aiClient = aiClient;
        this.l2AutoPass = l2AutoPass;
    }

    @Override
    public ModerationPlan evaluate(String targetType, String title, String body,
                                   String questionTitle, String authorRole, boolean highFrequency) {
        L1Result l1 = l1(targetType, title, body, highFrequency);
        if ("block".equals(l1.decision())) {
            return new ModerationPlan(ModerationStatus.REJECTED, Visibility.HIDDEN, RiskLevel.HIGH,
                    l1.reason(), l1, null);
        }

        L2Result l2 = l2(targetType, title, body, questionTitle, authorRole, l1.labels());
        Visibility visibility = "allow".equals(l2.decision()) ? Visibility.VISIBLE : Visibility.HIDDEN;
        String reason = l2.reason() != null && !l2.reason().isBlank()
                ? l2.reason()
                : l1.reason();
        RiskLevel risk = l2.risk() == null ? RiskLevel.UNRATED : l2.risk();
        if ("review".equals(l1.decision()) && risk != RiskLevel.HIGH) {
            risk = RiskLevel.WATCH;
        }
        return new ModerationPlan(ModerationStatus.L3, visibility, risk, reason, l1, l2);
    }

    @Override
    public void recordPlan(String targetType, Long targetId, ModerationPlan plan) {
        L1Result l1 = plan.l1();
        insert(targetType, targetId, STAGE_L1, "rule-v1",
                l1.labels(), l1Risk(l1), null, ModerationStatus.L1.name(),
                plan.rejected() ? ModerationStatus.REJECTED.name() : ModerationStatus.L2.name(),
                l1.decision(), null, l1.reason());

        if (plan.l2() == null) {
            return;
        }
        L2Result l2 = plan.l2();
        insert(targetType, targetId, STAGE_L2, l2AutoPass ? "l2-auto-pass" : "ai-v1",
                l2.labels(), l2.risk() == null ? RiskLevel.UNRATED : l2.risk(), l2.confidence(),
                ModerationStatus.L2.name(), ModerationStatus.L3.name(),
                l2.decision(), null, l2.reason());
    }

    @Override
    public void recordHuman(String targetType, Long targetId, String decision,
                            String fromStatus, String toStatus, String riskLevel,
                            Long operatorId, String note) {
        recordManual(targetType, targetId, STAGE_HUMAN, decision, fromStatus, toStatus,
                riskLevel, operatorId, note);
    }

    @Override
    public void recordManual(String targetType, Long targetId, String stage, String decision,
                             String fromStatus, String toStatus, String riskLevel,
                             Long operatorId, String note) {
        insert(targetType, targetId, stage, null, List.of(),
                RiskLevel.parse(riskLevel), null, fromStatus, toStatus,
                decision, operatorId, note);
    }

    private L1Result l1(String targetType, String title, String body, boolean highFrequency) {
        String text = ((title == null ? "" : title) + "\n" + (body == null ? "" : body)).trim();
        if (text.isBlank()) {
            return new L1Result("review", List.of("空内容"), "内容为空");
        }
        String normalized = text.toLowerCase();
        List<String> labels = new ArrayList<>();
        boolean block = false;
        for (ModerationWordDO item : wordDao.findAll()) {
            if (normalized.contains(item.getWord().toLowerCase())) {
                labels.add(item.getWord());
                if ("block".equals(item.getAction())) {
                    block = true;
                }
            }
        }
        for (String word : REVIEW_WORDS) {
            if (normalized.contains(word) && !labels.contains(word)) {
                labels.add(word);
            }
        }
        if (PHONE.matcher(text).find() && !labels.contains("手机号")) {
            labels.add("手机号");
        }
        if (URL.matcher(text).find() && !labels.contains("外链")) {
            labels.add("外链");
        }
        if (CONTACT.matcher(text).find() && !labels.contains("联系方式")) {
            labels.add("联系方式");
        }
        if (REPEATED.matcher(text).find() && !labels.contains("重复内容")) {
            labels.add("重复内容");
        }
        if (highFrequency) {
            labels.add("高频提交");
        }
        if (text.length() > 2000 && !labels.contains("超长内容")) {
            labels.add("超长内容");
        }
        String reason = labels.isEmpty() ? null : "命中：" + String.join("、", labels);
        if ("answer".equals(targetType) && text.length() < 2) {
            return new L1Result("review", List.of("内容过短"), "内容过短");
        }
        if (block) {
            return new L1Result("block", labels, reason);
        }
        if (!labels.isEmpty()) {
            return new L1Result("review", labels, reason);
        }
        return new L1Result("pass", List.of(), null);
    }

    private L2Result l2(String targetType, String title, String body,
                        String questionTitle, String authorRole, List<String> l1Labels) {
        if (l2AutoPass) {
            return new L2Result("allow", RiskLevel.NORMAL, List.of(), 1.0,
                    "AI 未接入，L2 配置自动通过");
        }
        try {
            ModerationAiResult result = aiClient.review(
                    new ModerationAiInput(targetType, title, body, questionTitle, authorRole, l1Labels));
            if (result == null) {
                return new L2Result("review", RiskLevel.UNRATED, List.of(), null, "AI 未返回结果");
            }
            RiskLevel risk = result.risk() == null ? RiskLevel.UNRATED : result.risk();
            return new L2Result(result.decision(), risk,
                    result.labels() == null ? List.of() : result.labels(),
                    result.confidence(), result.reason());
        } catch (RuntimeException ex) {
            return new L2Result("review", RiskLevel.UNRATED, List.of(), null, "AI 审核异常：" + ex.getMessage());
        }
    }

    private RiskLevel l1Risk(L1Result l1) {
        return switch (l1.decision()) {
            case "block" -> RiskLevel.HIGH;
            case "review" -> RiskLevel.WATCH;
            default -> RiskLevel.NORMAL;
        };
    }

    private void insert(String targetType, Long targetId, String stage, String modelVersion,
                        List<String> labels, RiskLevel riskLevel, Double confidence,
                        String fromStatus, String toStatus, String decision,
                        Long operatorId, String note) {
        ModerationRecordDO record = new ModerationRecordDO();
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        record.setStage(stage);
        record.setModelVersion(modelVersion);
        record.setRiskLabels(labels == null || labels.isEmpty() ? null : String.join(",", labels));
        record.setRiskLevel(riskLevel == null ? null : riskLevel.name());
        record.setConfidence(confidence);
        record.setFromModerationStatus(fromStatus);
        record.setToModerationStatus(toStatus);
        record.setDecision(decision);
        record.setOperatorId(operatorId);
        record.setNote(note);
        recordDao.insert(record);
    }
}