package com.careerq.dataobject;

import java.time.LocalDateTime;

/**
 * 审核留痕：只增不改，记录每次机审/人审的判定。
 */
public class ModerationRecordDO {

    private Long id;

    private String targetType;

    private Long targetId;

    private String stage;

    private String modelVersion;

    private String riskLabels;

    private String decision;

    private Long operatorId;

    private String note;

    private LocalDateTime createdAt = LocalDateTime.now();

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
