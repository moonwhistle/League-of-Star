package com.sang.smite.domain.game.repository;

import com.sang.smite.domain.game.domain.GameRoom;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameRoomRepository extends JpaRepository<GameRoom, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameRoom g where g.id = :gameRoomId")
    Optional<GameRoom> findByIdForUpdate(@Param("gameRoomId") Long gameRoomId);

    @Query("""
            select g.id
            from GameRoom g
            where g.status = :status
              and (
                    select count(r)
                    from GameRecord r
                    where r.gameRoomId = g.id
                  ) <> :expectedRecordCount
            order by g.id asc
            """)
    List<Long> findGameRoomIdsByStatusAndRecordCountNot(
            @Param("status") GameStatus status,
            @Param("expectedRecordCount") long expectedRecordCount,
            Pageable pageable
    );
}
