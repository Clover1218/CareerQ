package com.careerq.answer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    List<Answer> findByQuestionIdAndStatusOrderByIdAsc(Long questionId, String status);

    List<Answer> findByStatusOrderByIdAsc(String status);

    long countByStatus(String status);
}
