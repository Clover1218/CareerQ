package com.careerq.moderation;

/**
 * 面向前端展示的派生状态，由审核态与可见态共同决定。
 */
public enum DisplayStatus {
    REVIEWING,
    ARBITRATING,
    REJECTED,
    PUBLISHED,
    REMOVED,
    WITHDRAWN;

    public static DisplayStatus of(String moderationStatus, String visibility) {
        ModerationStatus moderation = ModerationStatus.parse(moderationStatus);
        Visibility visible = Visibility.parse(visibility);
        if (moderation == ModerationStatus.WITHDRAWN) {
            return WITHDRAWN;
        }
        if (visible == Visibility.REMOVED) {
            return REMOVED;
        }
        if (moderation == ModerationStatus.REJECTED) {
            return REJECTED;
        }
        if (moderation == ModerationStatus.APPROVED && visible == Visibility.VISIBLE) {
            return PUBLISHED;
        }
        return REVIEWING;
    }

    /** 后台视角：L3 且人工裁定满 2 次为待仲裁；作者/公开侧继续用 2 参版本。 */
    public static DisplayStatus of(String moderationStatus, String visibility, int reviewAttempts) {
        DisplayStatus base = of(moderationStatus, visibility);
        if (base == REVIEWING && reviewAttempts >= 2) {
            return ARBITRATING;
        }
        return base;
    }
}