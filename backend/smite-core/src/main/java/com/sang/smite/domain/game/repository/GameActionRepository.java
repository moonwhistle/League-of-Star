package com.sang.smite.domain.game.repository;

import com.sang.smite.domain.game.domain.GameAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameActionRepository extends JpaRepository<GameAction, Long> {

    Optional<GameAction> findByGameRoomIdAndUserId(Long gameRoomId, Long userId);

    List<GameAction> findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(Long gameRoomId);
}
