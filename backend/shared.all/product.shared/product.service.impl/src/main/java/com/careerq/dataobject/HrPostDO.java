package com.careerq.dataobject;

import java.time.LocalDateTime;

public class HrPostDO {
    private Long id;
    private Long authorId;
    private String clientKey;
    private String title;
    private String body;
    private String status;
    private long version;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String authorNickname;
    private String authorAvatarUrl;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long value) { authorId = value; }
    public String getClientKey() { return clientKey; }
    public void setClientKey(String value) { clientKey = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getBody() { return body; }
    public void setBody(String value) { body = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime value) { submittedAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
    public String getAuthorNickname() { return authorNickname; }
    public void setAuthorNickname(String value) { authorNickname = value; }
    public String getAuthorAvatarUrl() { return authorAvatarUrl; }
    public void setAuthorAvatarUrl(String value) { authorAvatarUrl = value; }
}
