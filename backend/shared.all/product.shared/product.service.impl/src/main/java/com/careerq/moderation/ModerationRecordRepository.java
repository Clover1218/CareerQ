package com.careerq.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationRecordRepository extends JpaRepository<ModerationRecord, Long> {
}
