package com.sang.leagueofstar.domain.customgame.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameParticipant;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomParticipantRole;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomStatus;
import com.sang.leagueofstar.domain.customgame.repository.CustomGameParticipantRepository;
import com.sang.leagueofstar.domain.customgame.repository.CustomGameRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomGameRoomCommandService {

    private static final int MAX_INVITE_CODE_GENERATION_ATTEMPTS = 10;
    private static final String WAITING_OWNER_UNIQUE_CONSTRAINT = "uk_custom_game_rooms_waiting_owner";

    private final CustomGameRoomRepository customGameRoomRepository;
    private final CustomGameParticipantRepository customGameParticipantRepository;
    private final CustomRoomInviteCodeGenerator inviteCodeGenerator;

    public CustomGameRoom createRoom(Long ownerUserId) {
        validateOwnerUserId(ownerUserId);
        validateNoWaitingRoom(ownerUserId);

        CustomGameRoom customGameRoom = saveRoomWithUniqueRetry(ownerUserId);
        customGameParticipantRepository.save(
                CustomGameParticipant.create(customGameRoom.getId(), ownerUserId, CustomRoomParticipantRole.OWNER)
        );
        return customGameRoom;
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

    private CustomGameRoom saveRoomWithUniqueRetry(Long ownerUserId) {
        for (int attempt = 0; attempt < MAX_INVITE_CODE_GENERATION_ATTEMPTS; attempt++) {
            String inviteCode = inviteCodeGenerator.generate();
            if (customGameRoomRepository.existsByInviteCode(inviteCode)) {
                continue;
            }
            try {
                return customGameRoomRepository.saveAndFlush(
                        CustomGameRoom.create(ownerUserId, inviteCode)
                );
            } catch (DataIntegrityViolationException exception) {
                if (isWaitingOwnerUniqueViolation(exception)) {
                    throw new CoreException(CoreErrorCode.CUSTOM_ROOM_ACTIVE_EXISTS);
                }
                throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVITE_CODE_GENERATION_FAILED);
            }
        }
        throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVITE_CODE_GENERATION_FAILED);
    }

    private boolean isWaitingOwnerUniqueViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(WAITING_OWNER_UNIQUE_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
