package com.careerq.moderation;

/**
 * 兼容旧客户端的聚合状态；真实状态以 moderationStatus 与 visibility 为准。
 */
public enum LegacyStatus {
    PUBLISHED("published"),
    PENDING_MACHINE("pending_machine"),
    PENDING_HUMAN("pending_human"),
    REJECTED("rejected"),
    REMOVED("removed");

    private final String value;

    LegacyStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static LegacyStatus of(String moderationStatus, String visibility) {
        if (Visibility.parse(visibility) == Visibility.REMOVED) {
            return REMOVED;
        }
        if (ModerationStatus.parse(moderationStatus) == ModerationStatus.REJECTED) {
            return REJECTED;
        }
        if (Visibility.parse(visibility) == Visibility.VISIBLE) {
            return PUBLISHED;
        }
        ModerationStatus moderation = ModerationStatus.parse(moderationStatus);
        if (moderation == ModerationStatus.L1 || moderation == ModerationStatus.L2) {
            return PENDING_MACHINE;
        }
        return PENDING_HUMAN;
    }
}