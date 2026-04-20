package com.sang.smite.domain.game.repository;

import com.sang.smite.domain.game.domain.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRoomRepository extends JpaRepository<GameRoom, Long> {
}
