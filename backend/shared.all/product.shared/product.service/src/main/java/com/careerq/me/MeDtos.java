package com.careerq.me;

public final class MeDtos {

    private MeDtos() {
    }

    /**
     * 个人主页统计。
     * receivedAnswerCount：我的问题收到的回答数（问题 answer_count 之和）。
     * authoredAnswerCount：我发表的回答数（已发布的回答）。
     * likeCount：我的问题与我发表的回答获得的点赞总和。
     */
    public record Stats(
            long questionCount,
            long receivedAnswerCount,
            long authoredAnswerCount,
            long likeCount) {
    }
}
