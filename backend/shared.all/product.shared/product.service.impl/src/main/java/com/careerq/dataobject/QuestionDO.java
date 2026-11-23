package com.careerq.dataobject;

import com.careerq.moderation.LegacyStatus;

import java.time.LocalDateTime;

public class QuestionDO {

    private Long id;

    private String scope;

    private Long authorId;

    private String title;

    private String body;

    private String moderationStatus;

    private String visibility;

    private String riskLevel;

    private String moderationReason;

    private LocalDateTime moderatedAt;

    private int reviewAttempts;

    private LocalDateTime firstAnswerAt;

    private int answerCount;

    private Boolean hasPendingAnswer;

    private int followupCount;

    private int usefulCount;

    private Integer likeCount;

    private Integer favoriteCount;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
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

    public LocalDateTime getFirstAnswerAt() {
        return firstAnswerAt;
    }

    public int getAnswerCount() {
        return answerCount;
    }

    public Boolean getHasPendingAnswer() {
        return hasPendingAnswer;
    }

    public void setHasPendingAnswer(Boolean hasPendingAnswer) {
        this.hasPendingAnswer = hasPendingAnswer;
    }

    public int getFollowupCount() {
        return followupCount;
    }

    public void setFollowupCount(int followupCount) {
        this.followupCount = followupCount;
    }

    public int getLikeCount() {
        return likeCount == null ? 0 : likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public int getFavoriteCount() {
        return favoriteCount == null ? 0 : favoriteCount;
    }

    public void setFavoriteCount(Integer favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public int getUsefulCount() {
        return usefulCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
