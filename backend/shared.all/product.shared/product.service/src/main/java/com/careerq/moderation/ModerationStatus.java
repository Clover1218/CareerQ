package com.careerq.moderation;

/**
 * 内容审核态：L1/L2/L3 表示正在某一层审核，APPROVED/REJECTED/WITHDRAWN 为终态。
 */
public enum ModerationStatus {
    L1,
    L2,
    L3,
    APPROVED,
    REJECTED,
    WITHDRAWN;

    public static ModerationStatus parse(String value) {
        if (value == null) {
            return null;
        }
        for (ModerationStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }

    public boolean inProgress() {
        return this == L1 || this == L2 || this == L3;
    }
}