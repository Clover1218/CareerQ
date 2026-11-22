package com.careerq.dataobject;

import java.time.LocalDateTime;

public class AnswerDO {

    private Long id;

    private Long questionId;

    private Long followupId;

    private Long authorId;

    private String body;

    private String lengthType;

    private String moderationStatus;

    private String visibility;

    private String riskLevel;

    private String moderationReason;

    private LocalDateTime moderatedAt;

    private int reviewAttempts;

    private int likeCount;

    private int usefulCount;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getFollowupId() {
        return followupId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public void setFollowupId(Long followupId) {
        this.followupId = followupId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getLengthType() {
        return lengthType;
    }

    public void setLengthType(String lengthType) {
        this.lengthType = lengthType;
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

    public int getLikeCount() {
        return likeCount;
    }

    public int getUsefulCount() {
        return usefulCount;
    }

    public void setUsefulCount(int usefulCount) {
        this.usefulCount = usefulCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
