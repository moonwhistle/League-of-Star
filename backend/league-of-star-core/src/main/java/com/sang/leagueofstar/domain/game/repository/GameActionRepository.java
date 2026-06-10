package com.sang.leagueofstar.domain.game.repository;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameActionRepository extends JpaRepository<GameAction, Long> {

    List<GameAction> findByGameRoomIdOrderByServerReceiveTimeMsAscIdAsc(Long gameRoomId);
}
