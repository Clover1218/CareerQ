package com.careerq.moderation;

/**
 * 内容可见态，与审核态正交。
 */
public enum Visibility {
    HIDDEN,
    VISIBLE,
    REMOVED;

    public static Visibility parse(String value) {
        if (value == null) {
            return null;
        }
        for (Visibility visibility : values()) {
            if (visibility.name().equalsIgnoreCase(value.trim())) {
                return visibility;
            }
        }
        return null;
    }
}