package com.sang.leagueofstar.domain.customgame.repository;

import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomGameRoomRepository extends JpaRepository<CustomGameRoom, Long> {

    boolean existsByOwnerUserIdAndStatus(Long ownerUserId, CustomRoomStatus status);

    boolean existsByInviteCode(String inviteCode);

    Optional<CustomGameRoom> findByInviteCode(String inviteCode);

    List<CustomGameRoom> findByStatusOrderByIdAsc(CustomRoomStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from CustomGameRoom room where room.inviteCode = :inviteCode")
    Optional<CustomGameRoom> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from CustomGameRoom room where room.id = :roomId")
    Optional<CustomGameRoom> findByIdForUpdate(@Param("roomId") Long roomId);
}
