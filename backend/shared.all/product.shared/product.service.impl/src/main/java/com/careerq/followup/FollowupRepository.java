package com.careerq.followup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FollowupRepository extends JpaRepository<Followup, Long> {

    List<Followup> findByQuestionIdAndStatusOrderByIdAsc(Long questionId, String status);
}
