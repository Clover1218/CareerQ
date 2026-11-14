package com.careerq.dataobject;

import java.time.LocalDateTime;

/**
 * 可运营维护的审核词库。action: block（直接拒）/ review（转人工）。
 */
public class ModerationWordDO {

    private Long id;

    private String word;

    private String category;

    private String action;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getWord() {
        return word;
    }

    public String getCategory() {
        return category;
    }

    public String getAction() {
        return action;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
