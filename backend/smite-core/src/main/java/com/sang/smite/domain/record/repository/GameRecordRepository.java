package com.sang.smite.domain.record.repository;

import com.sang.smite.domain.record.domain.GameRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameRecordRepository extends JpaRepository<GameRecord, Long> {

    long countByGameRoomId(Long gameRoomId);

    List<GameRecord> findByGameRoomId(Long gameRoomId);
}
