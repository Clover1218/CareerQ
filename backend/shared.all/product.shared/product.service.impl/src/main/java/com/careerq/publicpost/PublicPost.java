package com.careerq.publicpost;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 私密问答脱敏后的公开沉淀。需经二次审核（M5）后才会对外展示。
 */
@Entity
@Table(name = "public_posts")
public class PublicPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_question_id")
    private Long originalQuestionId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "question_body", nullable = false)
    private String questionBody;

    @Column(name = "hr_answer", nullable = false)
    private String hrAnswer;

    @Column(nullable = false)
    private boolean anonymized;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

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
