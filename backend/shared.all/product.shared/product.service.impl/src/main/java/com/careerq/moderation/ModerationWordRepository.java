package com.careerq.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModerationWordRepository extends JpaRepository<ModerationWord, Long> {

    Optional<ModerationWord> findByWord(String word);
}
