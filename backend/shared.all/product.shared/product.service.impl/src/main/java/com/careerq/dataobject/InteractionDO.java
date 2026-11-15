package com.careerq.dataobject;

import java.time.LocalDateTime;

public class InteractionDO {

    private Long id;

    private Long userId;

    private String targetType;

    private Long targetId;

    private String type;

    private LocalDateTime createdAt = LocalDateTime.now();

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getType() {
        return type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
