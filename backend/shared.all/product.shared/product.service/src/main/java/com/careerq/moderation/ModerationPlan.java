package com.careerq.moderation;

import java.util.List;

/**
 * 内容创建时 L1/L2 的完整审核结果，供业务表落库与留痕使用。
 */
public record ModerationPlan(
        ModerationStatus moderationStatus,
        Visibility visibility,
        RiskLevel riskLevel,
        String reason,
        L1Result l1,
        L2Result l2) {

    public record L1Result(String decision, List<String> labels, String reason) {
    }

    public record L2Result(String decision, RiskLevel risk, List<String> labels,
                           Double confidence, String reason) {
    }

    public boolean rejected() {
        return moderationStatus == ModerationStatus.REJECTED;
    }
}