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
}
