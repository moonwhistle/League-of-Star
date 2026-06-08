package com.sang.leagueofstar.domain.game.repository;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameActionRepository extends JpaRepository<GameAction, Long> {

    Optional<GameAction> findByGameRoomIdAndUserId(Long gameRoomId, Long userId);

    List<GameAction> findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(Long gameRoomId);
}
