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
        // L2 通过后内容即公开，人工复核（L3）不再影响公开侧展示。
        if (visible == Visibility.VISIBLE
                && (moderation == ModerationStatus.APPROVED || moderation == ModerationStatus.L3)) {
            return PUBLISHED;
        }
        return REVIEWING;
    }

    /** 后台视角：L3 一律视为待审，满 2 次人工裁定为待仲裁；公开侧用 2 参版本。 */
    public static DisplayStatus adminOf(String moderationStatus, String visibility, int reviewAttempts) {
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
        return reviewAttempts >= 2 ? ARBITRATING : REVIEWING;
    }
}