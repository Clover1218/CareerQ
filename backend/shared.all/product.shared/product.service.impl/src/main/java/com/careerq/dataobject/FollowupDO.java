package com.careerq.dataobject;

import java.time.LocalDateTime;

/**
 * 学生在公开问题下的追问。
 */
public class FollowupDO {

    private Long id;

    private Long questionId;

    /** 追问目标 HR：会话线程按 (questionId, hrId) 划分。 */
    private Long hrId;

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
