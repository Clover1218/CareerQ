package com.careerq.service;

import com.careerq.moderation.ModerationPlan;

public interface ModerationService {

    /**
     * 同步执行 L1 关键词/规则与 L2 初审，返回内容落库时应使用的状态。
     * L2 未接入 AI 时由配置决定自动通过。
     */
    ModerationPlan evaluate(String targetType, String title, String body,
                            String questionTitle, String authorRole, boolean highFrequency);

    void recordPlan(String targetType, Long targetId, ModerationPlan plan);

    void recordHuman(String targetType, Long targetId, String decision,
                     String fromStatus, String toStatus, String riskLevel,
                     Long operatorId, String note);

    /** 人工留痕，stage 为 human 或 ARBITRATE。 */
    void recordManual(String targetType, Long targetId, String stage, String decision,
                      String fromStatus, String toStatus, String riskLevel,
                      Long operatorId, String note);
}