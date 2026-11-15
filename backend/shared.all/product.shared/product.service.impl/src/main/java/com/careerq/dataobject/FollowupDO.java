package com.careerq.dataobject;

import java.time.LocalDateTime;

/**
 * 追问：公开问题与私密工单共用，用 scope 区分可见范围。
 */
public class FollowupDO {

    private Long id;

    private Long questionId;

    private Long senderId;

    private String senderRole;

    private String body;

    private String scope;

    private String status;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
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

    public void setBody(String body) {
        this.body = body;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
