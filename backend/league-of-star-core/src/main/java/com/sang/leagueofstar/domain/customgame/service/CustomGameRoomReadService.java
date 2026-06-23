package com.sang.leagueofstar.domain.customgame.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameParticipant;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomStatus;
import com.sang.leagueofstar.domain.customgame.repository.CustomGameParticipantRepository;
import com.sang.leagueofstar.domain.customgame.repository.CustomGameRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomGameRoomReadService {

    private final CustomGameRoomRepository customGameRoomRepository;
    private final CustomGameParticipantRepository customGameParticipantRepository;

    public CustomGameRoom getWaitingRoomByInviteCode(String inviteCode) {
        CustomGameRoom customGameRoom = customGameRoomRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new CoreException(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));
        if (!customGameRoom.isWaiting()) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE);
        }
        return customGameRoom;
    }

    public List<CustomGameRoom> findWaitingRooms() {
        return customGameRoomRepository.findByStatusOrderByIdAsc(CustomRoomStatus.WAITING);
    }

    public List<CustomGameParticipant> getParticipants(Long customRoomId) {
        return customGameParticipantRepository.findByCustomRoomIdOrderByIdAsc(customRoomId);
    }

    public List<CustomGameParticipant> findParticipantsByRoomIds(Collection<Long> customRoomIds) {
        if (customRoomIds.isEmpty()) {
            return List.of();
        }
        return customGameParticipantRepository.findByCustomRoomIdInOrderByCustomRoomIdAscIdAsc(customRoomIds);
    }
}
