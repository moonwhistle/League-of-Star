package com.sang.leagueofstar.domain.customgame.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomStatus;
import com.sang.leagueofstar.domain.customgame.repository.CustomGameRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomGameRoomCommandService {

    private static final int MAX_INVITE_CODE_GENERATION_ATTEMPTS = 10;

    private final CustomGameRoomRepository customGameRoomRepository;
    private final CustomRoomInviteCodeGenerator inviteCodeGenerator;

    public CustomGameRoom createRoom(Long ownerUserId) {
        validateOwnerUserId(ownerUserId);
        validateNoWaitingRoom(ownerUserId);

        CustomGameRoom customGameRoom = CustomGameRoom.create(ownerUserId, generateUniqueInviteCode());
        return customGameRoomRepository.save(customGameRoom);
    }

    private void validateOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT);
        }
    }

    private void validateNoWaitingRoom(Long ownerUserId) {
        if (customGameRoomRepository.existsByOwnerUserIdAndStatus(ownerUserId, CustomRoomStatus.WAITING)) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_ACTIVE_EXISTS);
        }
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < MAX_INVITE_CODE_GENERATION_ATTEMPTS; attempt++) {
            String inviteCode = inviteCodeGenerator.generate();
            if (!customGameRoomRepository.existsByInviteCode(inviteCode)) {
                return inviteCode;
            }
        }
        throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVITE_CODE_GENERATION_FAILED);
    }
}
