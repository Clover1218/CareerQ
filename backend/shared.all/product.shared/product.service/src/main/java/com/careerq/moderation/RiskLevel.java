package com.careerq.moderation;

/**
 * 审核风险等级：L2 AI 输出，L1 命中 review 时默认 watch。
 */
public enum RiskLevel {
    NORMAL,
    WATCH,
    HIGH,
    UNRATED;

    public static RiskLevel parse(String value) {
        if (value == null) {
            return null;
        }
        for (RiskLevel level : values()) {
            if (level.name().equalsIgnoreCase(value.trim())) {
                return level;
            }
        }
        return null;
    }
}