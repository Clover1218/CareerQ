package com.careerq.dataobject;

import java.time.LocalDateTime;

public class AnswerDO {

    private Long id;

    private Long questionId;

    private Long followupId;

    private Long authorId;

    private String body;

    private String lengthType;

    private String status;

    private String removalSource;

    private Long removedBy;

    private LocalDateTime removedAt;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemovalSource() {
        return removalSource;
    }

    public void setRemovalSource(String removalSource) {
        this.removalSource = removalSource;
    }

    public Long getRemovedBy() {
        return removedBy;
    }

    public void setRemovedBy(Long removedBy) {
        this.removedBy = removedBy;
    }

    public LocalDateTime getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(LocalDateTime removedAt) {
        this.removedAt = removedAt;
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
