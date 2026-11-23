package com.careerq.dataobject;

import com.careerq.moderation.LegacyStatus;

import java.time.LocalDateTime;

/**
 * 学生在公开问题下的追问。
 */
public class FollowupDO {

    private Long id;

    private Long questionId;

    /** 追问目标 HR：会话线程按 (questionId, hrId) 划分。 */
    private Long hrId;

    private Long rootAnswerId;

    public Long getRootAnswerId() { return rootAnswerId; }

    public void setRootAnswerId(Long rootAnswerId) { this.rootAnswerId = rootAnswerId; }

    private Long senderId;

    private String senderRole;

    private String body;

    private String scope;

    private String moderationStatus;

    private String visibility;

    private String riskLevel;

    private String moderationReason;

    private LocalDateTime moderatedAt;

    private int reviewAttempts;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Long getHrId() {
        return hrId;
    }

    public void setHrId(Long hrId) {
        this.hrId = hrId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getSenderRole() {
        return senderRole;
    }

    public void setSenderRole(String senderRole) {
        this.senderRole = senderRole;
    }

    public String getBody() {
        return body;
    }

    public String getScope() {
        return scope;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getModerationStatus() {
        return moderationStatus;
    }

    public void setModerationStatus(String moderationStatus) {
        this.moderationStatus = moderationStatus;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    /** 兼容旧调用方的聚合状态；真实状态以 moderationStatus 与 visibility 为准。 */
    public String getStatus() {
        return LegacyStatus.of(moderationStatus, visibility).value();
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getModerationReason() {
        return moderationReason;
    }

    public void setModerationReason(String moderationReason) {
        this.moderationReason = moderationReason;
    }

    public LocalDateTime getModeratedAt() {
        return moderatedAt;
    }

    public void setModeratedAt(LocalDateTime moderatedAt) {
        this.moderatedAt = moderatedAt;
    }

    public int getReviewAttempts() {
        return reviewAttempts;
    }

    public void setReviewAttempts(int reviewAttempts) {
        this.reviewAttempts = reviewAttempts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
