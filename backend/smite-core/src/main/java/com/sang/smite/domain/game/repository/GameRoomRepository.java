package com.sang.smite.domain.game.repository;

import com.sang.smite.domain.game.domain.GameRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GameRoomRepository extends JpaRepository<GameRoom, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameRoom g where g.id = :gameRoomId")
    Optional<GameRoom> findByIdForUpdate(@Param("gameRoomId") Long gameRoomId);
}
