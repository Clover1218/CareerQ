package com.careerq.roundtable;

import com.careerq.common.UserBrief;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

public final class RoundtableDtos {

    private RoundtableDtos() {}

    public record TopicChoice(@NotNull LocalDate week, @NotNull @Positive Long questionId) {}

    public record Candidate(long questionId, String title, long votes) {}

    public record Seat(long answerId, UserBrief author) {}

    public record Featured(long questionId, String title, long answerCount, int authorCount, List<Seat> seats) {}

    public record Roundtable(LocalDate week, LocalDate closesOn, Featured featured,
                             List<Candidate> candidates, Long myVote) {}
}
