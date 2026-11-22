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

    private String riskLevel;

    private Double confidence;

    private String fromModerationStatus;

    private String toModerationStatus;

    private String decision;

    private Long operatorId;

    private String note;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getRiskLabels() {
        return riskLabels;
    }

    public void setRiskLabels(String riskLabels) {
        this.riskLabels = riskLabels;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getFromModerationStatus() {
        return fromModerationStatus;
    }

    public void setFromModerationStatus(String fromModerationStatus) {
        this.fromModerationStatus = fromModerationStatus;
    }

    public String getToModerationStatus() {
        return toModerationStatus;
    }

    public void setToModerationStatus(String toModerationStatus) {
        this.toModerationStatus = toModerationStatus;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}