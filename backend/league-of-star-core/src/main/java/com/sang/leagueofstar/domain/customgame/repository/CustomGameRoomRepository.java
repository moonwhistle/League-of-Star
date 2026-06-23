package com.sang.leagueofstar.domain.customgame.repository;

import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomGameRoomRepository extends JpaRepository<CustomGameRoom, Long> {

    boolean existsByOwnerUserIdAndStatus(Long ownerUserId, CustomRoomStatus status);

    boolean existsByInviteCode(String inviteCode);

    @EntityGraph(attributePaths = "participants")
    Optional<CustomGameRoom> findByInviteCode(String inviteCode);

    @EntityGraph(attributePaths = "participants")
    List<CustomGameRoom> findByStatusOrderByIdAsc(CustomRoomStatus status);
}
