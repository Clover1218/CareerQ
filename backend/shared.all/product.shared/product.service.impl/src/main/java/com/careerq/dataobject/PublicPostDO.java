package com.careerq.dataobject;

import java.time.LocalDateTime;

/** 已审核的公开问答沉淀内容。 */
public class PublicPostDO {

    private Long id;

    private Long originalQuestionId;

    private String title;

    private String questionBody;

    private String hrAnswer;

    private boolean anonymized;

    private String status;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public Long getOriginalQuestionId() {
        return originalQuestionId;
    }

    public void setOriginalQuestionId(Long originalQuestionId) {
        this.originalQuestionId = originalQuestionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getQuestionBody() {
        return questionBody;
    }

    public void setQuestionBody(String questionBody) {
        this.questionBody = questionBody;
    }

    public String getHrAnswer() {
        return hrAnswer;
    }

    public void setHrAnswer(String hrAnswer) {
        this.hrAnswer = hrAnswer;
    }

    public boolean isAnonymized() {
        return anonymized;
    }

    public void setAnonymized(boolean anonymized) {
        this.anonymized = anonymized;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
