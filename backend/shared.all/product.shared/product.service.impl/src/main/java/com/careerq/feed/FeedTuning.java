package com.careerq.feed;

/** Feed 推荐的集中调参点；先给默认值，灰度期只改这里。 */
public final class FeedTuning {

    public static final double LIKE_WEIGHT = 1.0;
    public static final double FAVORITE_WEIGHT = 3.0;
    public static final double USEFUL_WEIGHT = 1.5;
    public static final double DISLIKE_WEIGHT = -5.0;

    public static final double AFFINITY_HALF_LIFE_DAYS = 30.0;
    public static final double RECENCY_HALF_LIFE_HOURS = 168.0;

    public static final double AFFINITY_WEIGHT = 0.55;
    public static final double QUALITY_WEIGHT = 0.20;
    public static final double RECENCY_WEIGHT = 0.20;
    public static final double EXPLORATION = 0.05;
    public static final double COLD_EXPLORATION = 0.10;

    public static final double MAX_AFFINITY = 20.0;
    public static final double MAX_QUESTION_AFFINITY = 10.0;
    public static final double HARD_NEGATIVE_THRESHOLD = -8.0;

    public static final int CANDIDATE_LIMIT = 500;
    public static final int CANDIDATE_WINDOW_DAYS = 60;
    public static final int MAX_PAGE = 25;

    private FeedTuning() {
    }

    public static double weightOf(String type) {
        return switch (type) {
            case "like" -> LIKE_WEIGHT;
            case "favorite" -> FAVORITE_WEIGHT;
            case "useful" -> USEFUL_WEIGHT;
            case "dislike" -> DISLIKE_WEIGHT;
            default -> 0.0;
        };
    }
}
