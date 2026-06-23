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

import java.time.LocalDateTime;

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

    public CustomGameRoom joinRoom(String inviteCode, Long userId) {
        validateInviteCode(inviteCode);
        validateParticipantUserId(userId);

        CustomGameRoom customGameRoom = getWaitingRoomByInviteCodeForUpdate(inviteCode);
        Long customRoomId = customGameRoom.getId();
        if (customGameParticipantRepository.existsByCustomRoomIdAndUserId(customRoomId, userId)) {
            return customGameRoom;
        }
        if (customGameParticipantRepository.countByCustomRoomId(customRoomId) >= CustomGameRoom.MAX_PARTICIPANTS) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_FULL);
        }
        savePlayerParticipant(customRoomId, userId);
        return customGameRoom;
    }

    public CustomGameRoom leaveRoom(Long roomId, Long userId) {
        validateRoomId(roomId);
        validateParticipantUserId(userId);

        CustomGameRoom customGameRoom = getWaitingRoomByIdForUpdate(roomId);
        if (!customGameParticipantRepository.existsByCustomRoomIdAndUserId(roomId, userId)) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT);
        }
        if (customGameRoom.getOwnerUserId().equals(userId)) {
            customGameRoom.close(LocalDateTime.now());
            customGameParticipantRepository.deleteByCustomRoomId(roomId);
            return customGameRoom;
        }
        customGameParticipantRepository.deleteByCustomRoomIdAndUserId(roomId, userId);
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

    private void validateParticipantUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT);
        }
    }

    private void validateRoomId(Long roomId) {
        if (roomId == null) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND);
        }
    }

    private void validateInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_INVITE_CODE);
        }
    }

    private CustomGameRoom getWaitingRoomByInviteCodeForUpdate(String inviteCode) {
        CustomGameRoom customGameRoom = customGameRoomRepository.findByInviteCodeForUpdate(inviteCode)
                .orElseThrow(() -> new CoreException(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));
        validateWaitingRoom(customGameRoom);
        return customGameRoom;
    }

    private CustomGameRoom getWaitingRoomByIdForUpdate(Long roomId) {
        CustomGameRoom customGameRoom = customGameRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new CoreException(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));
        validateWaitingRoom(customGameRoom);
        return customGameRoom;
    }

    private void validateWaitingRoom(CustomGameRoom customGameRoom) {
        if (!customGameRoom.isWaiting()) {
            throw new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE);
        }
    }

    private void savePlayerParticipant(Long customRoomId, Long userId) {
        customGameParticipantRepository.save(
                CustomGameParticipant.create(customRoomId, userId, CustomRoomParticipantRole.PLAYER)
        );
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
