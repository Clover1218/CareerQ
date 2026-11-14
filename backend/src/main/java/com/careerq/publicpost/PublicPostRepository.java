package com.careerq.publicpost;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublicPostRepository extends JpaRepository<PublicPost, Long> {

    Optional<PublicPost> findByOriginalQuestionId(Long originalQuestionId);
}
