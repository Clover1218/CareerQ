package com.careerq.dataobject;

import java.time.LocalDateTime;

public class QuestionDO {

    private Long id;

    private String scope;

    private Long authorId;

    private String title;

    private String body;

    private String status;

    private Long assignedHrId;

    private LocalDateTime closedAt;

    private LocalDateTime firstAnswerAt;

    private int answerCount;

    private int usefulCount;

    private Integer likeCount;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAssignedHrId() {
        return assignedHrId;
    }

    public void setAssignedHrId(Long assignedHrId) {
        this.assignedHrId = assignedHrId;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public LocalDateTime getFirstAnswerAt() {
        return firstAnswerAt;
    }

    public int getAnswerCount() {
        return answerCount;
    }

    public int getLikeCount() {
        return likeCount == null ? 0 : likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public int getUsefulCount() {
        return usefulCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
