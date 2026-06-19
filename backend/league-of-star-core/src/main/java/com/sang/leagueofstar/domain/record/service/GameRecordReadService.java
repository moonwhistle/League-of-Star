package com.sang.leagueofstar.domain.record.service;

import com.sang.leagueofstar.domain.record.domain.GameRecord;
import com.sang.leagueofstar.domain.record.repository.GameRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameRecordReadService {

    private final GameRecordRepository gameRecordRepository;

    public long countByGameRoomId(Long gameRoomId) {
        return gameRecordRepository.countByGameRoomId(gameRoomId);
    }

    public long countByUserId(Long userId) {
        return gameRecordRepository.countByUserId(userId);
    }

    public List<GameRecord> findByGameRoomId(Long gameRoomId) {
        return gameRecordRepository.findByGameRoomId(gameRoomId);
    }

    public List<GameRecord> findRecentByUserId(Long userId, Pageable pageable) {
        return gameRecordRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
    }
}
