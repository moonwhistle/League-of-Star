package com.sang.leagueofstar.domain.customgame.repository;

import com.sang.leagueofstar.domain.customgame.domain.CustomGameParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CustomGameParticipantRepository extends JpaRepository<CustomGameParticipant, Long> {

    long countByCustomRoomId(Long customRoomId);

    boolean existsByCustomRoomIdAndUserId(Long customRoomId, Long userId);

    List<CustomGameParticipant> findByCustomRoomIdOrderByIdAsc(Long customRoomId);

    List<CustomGameParticipant> findByCustomRoomIdInOrderByCustomRoomIdAscIdAsc(Collection<Long> customRoomIds);
}
