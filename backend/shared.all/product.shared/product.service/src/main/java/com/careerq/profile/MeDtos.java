package com.careerq.profile;

public final class MeDtos {

    private MeDtos() {
    }

    public record Stats(long questionCount, long receivedAnswerCount,
                        long authoredAnswerCount, long likeCount) {
    }
}
