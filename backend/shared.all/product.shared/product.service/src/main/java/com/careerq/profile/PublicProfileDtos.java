package com.careerq.profile;

import java.time.LocalDateTime;
import java.util.List;

public final class PublicProfileDtos {
    private PublicProfileDtos() {}

    public record Statistics(long questionCount, long receivedAnswerCount, long rootAnswerCount,
                             long followupReplyCount, long likeCount, long usefulCount) {}

    public record Profile(Long id, String role, String nickname, String avatarUrl,
                          String school, String major, String grade, String companyDisplay,
                          String industry, String position, String bio, List<String> specialties,
                          String verifyStatus, Statistics stats) {}

    public record QuestionEntry(Long id, String title, String excerpt, LocalDateTime createdAt,
                                int answerCount, int followupCount) {}

    public record AnswerEntry(Long id, Long questionId, Long followupId, String title, String excerpt,
                              LocalDateTime createdAt, int likeCount, int usefulCount) {}
}
