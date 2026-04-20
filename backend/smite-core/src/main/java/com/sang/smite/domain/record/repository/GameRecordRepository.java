package com.sang.smite.domain.record.repository;

import com.sang.smite.domain.record.domain.GameRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRecordRepository extends JpaRepository<GameRecord, Long> {
}
