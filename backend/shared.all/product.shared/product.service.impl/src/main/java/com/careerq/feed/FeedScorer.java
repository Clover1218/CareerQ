package com.careerq.feed;

import com.careerq.dataobject.QuestionDO;

import java.time.Duration;
import java.time.LocalDateTime;

public final class FeedScorer {

    private FeedScorer() {
    }

    public static double score(QuestionDO question, double affinity, boolean hasAffinity,
                               long seed, LocalDateTime snapshotAt) {
        double affinityTerm = Math.tanh(
                clamp(affinity, -FeedTuning.MAX_QUESTION_AFFINITY, FeedTuning.MAX_QUESTION_AFFINITY) / 3.0);
        double exploration = (hasAffinity ? FeedTuning.EXPLORATION : FeedTuning.COLD_EXPLORATION)
                * jitter(seed, question.getId());
        return FeedTuning.AFFINITY_WEIGHT * affinityTerm
                + FeedTuning.QUALITY_WEIGHT * quality(question)
                + FeedTuning.RECENCY_WEIGHT * recency(question, snapshotAt)
                + exploration;
    }

    public static double hot(QuestionDO question, LocalDateTime snapshotAt) {
        double engagement = FeedTuning.HOT_BASELINE
                + FeedTuning.HOT_LIKE_WEIGHT * question.getLikeCount()
                + FeedTuning.HOT_FAVORITE_WEIGHT * question.getFavoriteCount()
                + FeedTuning.HOT_USEFUL_WEIGHT * question.getUsefulCount()
                + FeedTuning.HOT_REPLY_WEIGHT * (question.getAnswerCount() + question.getFollowupCount());
        return engagement * recency(question, snapshotAt);
    }

    public static double quality(QuestionDO question) {
        double reply = question.getAnswerCount() + question.getFollowupCount();
        double engagement = 3.0 * question.getFavoriteCount()
                + 2.0 * question.getLikeCount()
                + 1.5 * reply
                + question.getUsefulCount();
        return Math.tanh(Math.log1p(engagement) / 3.0);
    }

    public static double recency(QuestionDO question, LocalDateTime snapshotAt) {
        long minutes = Duration.between(question.getCreatedAt(), snapshotAt).toMinutes();
        double hours = Math.max(0, minutes / 60.0);
        return Math.pow(0.5, hours / FeedTuning.RECENCY_HALF_LIFE_HOURS);
    }

    /** 稳定哈希抖动：同一 seed + 问题 id 永远得到同一个 [0,1) 值，保证跨页排序一致。 */
    public static double jitter(long seed, long questionId) {
        long x = seed ^ (questionId * 0x9E3779B97F4A7C15L);
        x ^= (x >>> 33);
        x *= 0xFF51AFD7ED558CCDL;
        x ^= (x >>> 33);
        x *= 0xC4CEB9FE1A85EC53L;
        x ^= (x >>> 33);
        return (x >>> 11) * 0x1.0p-53;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
