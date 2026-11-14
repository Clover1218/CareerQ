package com.careerq.interaction;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractionRepository extends JpaRepository<Interaction, Long> {

    boolean existsByUserIdAndTargetTypeAndTargetIdAndType(
            Long userId, String targetType, Long targetId, String type);
}
