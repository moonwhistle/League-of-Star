package com.sang.leagueofstar.domain.game.repository;

import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface GameRoomRepository extends JpaRepository<GameRoom, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameRoom g where g.id = :gameRoomId")
    Optional<GameRoom> findByIdForUpdate(@Param("gameRoomId") Long gameRoomId);

    @Query("""
            select case when count(g) > 0 then true else false end
            from GameRoom g
            join g.participants p
            where p.userId = :userId
              and g.status in :statuses
            """)
    boolean existsByParticipantUserIdAndStatusIn(
            @Param("userId") Long userId,
            @Param("statuses") Collection<GameStatus> statuses
    );
}
