package com.sang.leagueofstar.domain.customgame.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameParticipant;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomParticipantRole;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomStatus;
import com.sang.leagueofstar.domain.customgame.repository.CustomGameParticipantRepository;
import com.sang.leagueofstar.domain.customgame.repository.CustomGameRoomRepository;
import com.sang.leagueofstar.domain.customgame.service.dto.CustomGameRoomStartResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomGameRoomCommandServiceTest {

    private static final Long OWNER_USER_ID = 1L;
    private static final Long PLAYER_USER_ID = 2L;
    private static final Long ROOM_ID = 100L;
    private static final String INVITE_CODE = "AB12CD";

    @InjectMocks
    private CustomGameRoomCommandService customGameRoomCommandService;

    @Mock
    private CustomGameRoomRepository customGameRoomRepository;

    @Mock
    private CustomGameParticipantRepository customGameParticipantRepository;

    @Mock
    private CustomRoomInviteCodeGenerator inviteCodeGenerator;

    @Test
    @DisplayName("createRoom - owner participant가 포함된 WAITING custom room을 저장한다")
    void createRoom_Success() {
        // given
        given(customGameRoomRepository.existsByOwnerUserIdAndStatus(OWNER_USER_ID, CustomRoomStatus.WAITING))
                .willReturn(false);
        given(inviteCodeGenerator.generate()).willReturn(INVITE_CODE);
        given(customGameRoomRepository.existsByInviteCode(INVITE_CODE)).willReturn(false);
        given(customGameRoomRepository.saveAndFlush(any(CustomGameRoom.class)))
                .willAnswer(invocation -> {
                    CustomGameRoom room = invocation.getArgument(0);
                    ReflectionTestUtils.setField(room, "id", 100L);
                    return room;
                });
        given(customGameParticipantRepository.save(any(CustomGameParticipant.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        CustomGameRoom result = customGameRoomCommandService.createRoom(OWNER_USER_ID);

        // then
        assertThat(result.getOwnerUserId()).isEqualTo(OWNER_USER_ID);
        assertThat(result.getInviteCode()).isEqualTo(INVITE_CODE);
        assertThat(result.getStatus()).isEqualTo(CustomRoomStatus.WAITING);
        verify(customGameRoomRepository).saveAndFlush(result);
        ArgumentCaptor<CustomGameParticipant> participantCaptor = ArgumentCaptor.forClass(CustomGameParticipant.class);
        verify(customGameParticipantRepository).save(participantCaptor.capture());
        assertThat(participantCaptor.getValue().getCustomRoomId()).isEqualTo(100L);
        assertThat(participantCaptor.getValue().getUserId()).isEqualTo(OWNER_USER_ID);
        assertThat(participantCaptor.getValue().getRole()).isEqualTo(CustomRoomParticipantRole.OWNER);
    }

    @Test
    @DisplayName("createRoom - ownerUserId가 null이면 예외를 던진다")
    void createRoom_NullOwner_ThrowException() {
        assertThatThrownBy(() -> customGameRoomCommandService.createRoom(null))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT));
    }

    @Test
    @DisplayName("createRoom - owner의 WAITING room이 이미 있으면 예외를 던진다")
    void createRoom_ActiveWaitingRoom_ThrowException() {
        // given
        given(customGameRoomRepository.existsByOwnerUserIdAndStatus(OWNER_USER_ID, CustomRoomStatus.WAITING))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.createRoom(OWNER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CoreErrorCode.CUSTOM_ROOM_ACTIVE_EXISTS));
    }

    @Test
    @DisplayName("createRoom - inviteCode가 충돌하면 다시 발급한다")
    void createRoom_RetryInviteCodeCollision() {
        // given
        given(customGameRoomRepository.existsByOwnerUserIdAndStatus(OWNER_USER_ID, CustomRoomStatus.WAITING))
                .willReturn(false);
        given(inviteCodeGenerator.generate()).willReturn("DUP123", INVITE_CODE);
        given(customGameRoomRepository.existsByInviteCode("DUP123")).willReturn(true);
        given(customGameRoomRepository.existsByInviteCode(INVITE_CODE)).willReturn(false);
        given(customGameRoomRepository.saveAndFlush(any(CustomGameRoom.class)))
                .willAnswer(invocation -> {
                    CustomGameRoom room = invocation.getArgument(0);
                    ReflectionTestUtils.setField(room, "id", 100L);
                    return room;
                });
        given(customGameParticipantRepository.save(any(CustomGameParticipant.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        CustomGameRoom result = customGameRoomCommandService.createRoom(OWNER_USER_ID);

        // then
        assertThat(result.getInviteCode()).isEqualTo(INVITE_CODE);
    }

    @Test
    @DisplayName("createRoom - WAITING unique 충돌이 발생하면 ACTIVE_EXISTS로 변환한다")
    void createRoom_WaitingOwnerUniqueConflict_ThrowActiveExists() {
        // given
        given(customGameRoomRepository.existsByOwnerUserIdAndStatus(OWNER_USER_ID, CustomRoomStatus.WAITING))
                .willReturn(false);
        given(inviteCodeGenerator.generate()).willReturn(INVITE_CODE);
        given(customGameRoomRepository.existsByInviteCode(INVITE_CODE)).willReturn(false);
        given(customGameRoomRepository.saveAndFlush(any(CustomGameRoom.class)))
                .willThrow(new DataIntegrityViolationException("uk_custom_game_rooms_waiting_owner"));

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.createRoom(OWNER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_ACTIVE_EXISTS));
        verify(customGameParticipantRepository, never()).save(any(CustomGameParticipant.class));
    }

    @Test
    @DisplayName("createRoom - inviteCode DB unique 충돌이 발생하면 도메인 예외로 변환한다")
    void createRoom_InviteCodeUniqueConflict_ThrowInviteCodeGenerationFailed() {
        // given
        given(customGameRoomRepository.existsByOwnerUserIdAndStatus(OWNER_USER_ID, CustomRoomStatus.WAITING))
                .willReturn(false);
        given(inviteCodeGenerator.generate()).willReturn(INVITE_CODE);
        given(customGameRoomRepository.existsByInviteCode(INVITE_CODE)).willReturn(false);
        given(customGameRoomRepository.saveAndFlush(any(CustomGameRoom.class)))
                .willThrow(new DataIntegrityViolationException("inviteCode unique conflict"));

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.createRoom(OWNER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVITE_CODE_GENERATION_FAILED));
        verify(customGameParticipantRepository, never()).save(any(CustomGameParticipant.class));
    }

    @Test
    @DisplayName("createRoom - inviteCode 충돌이 반복되면 예외를 던진다")
    void createRoom_InviteCodeGenerationFailed_ThrowException() {
        // given
        given(customGameRoomRepository.existsByOwnerUserIdAndStatus(OWNER_USER_ID, CustomRoomStatus.WAITING))
                .willReturn(false);
        given(inviteCodeGenerator.generate()).willReturn(INVITE_CODE);
        given(customGameRoomRepository.existsByInviteCode(INVITE_CODE)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.createRoom(OWNER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVITE_CODE_GENERATION_FAILED));
    }

    @Test
    @DisplayName("joinRoom - WAITING room에 PLAYER participant를 저장한다")
    void joinRoom_Success() {
        // given
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        given(customGameRoomRepository.findByInviteCodeForUpdate(INVITE_CODE)).willReturn(Optional.of(room));
        given(customGameParticipantRepository.existsByCustomRoomIdAndUserId(ROOM_ID, PLAYER_USER_ID))
                .willReturn(false);
        given(customGameParticipantRepository.countByCustomRoomId(ROOM_ID)).willReturn(1L);

        // when
        CustomGameRoom result = customGameRoomCommandService.joinRoom(INVITE_CODE, PLAYER_USER_ID);

        // then
        assertThat(result).isSameAs(room);
        ArgumentCaptor<CustomGameParticipant> participantCaptor = ArgumentCaptor.forClass(CustomGameParticipant.class);
        verify(customGameParticipantRepository).save(participantCaptor.capture());
        assertThat(participantCaptor.getValue().getCustomRoomId()).isEqualTo(ROOM_ID);
        assertThat(participantCaptor.getValue().getUserId()).isEqualTo(PLAYER_USER_ID);
        assertThat(participantCaptor.getValue().getRole()).isEqualTo(CustomRoomParticipantRole.PLAYER);
    }

    @Test
    @DisplayName("joinRoom - 이미 참가한 사용자는 participant를 추가하지 않고 room을 반환한다")
    void joinRoom_AlreadyParticipant_ReturnRoom() {
        // given
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        given(customGameRoomRepository.findByInviteCodeForUpdate(INVITE_CODE)).willReturn(Optional.of(room));
        given(customGameParticipantRepository.existsByCustomRoomIdAndUserId(ROOM_ID, PLAYER_USER_ID))
                .willReturn(true);

        // when
        CustomGameRoom result = customGameRoomCommandService.joinRoom(INVITE_CODE, PLAYER_USER_ID);

        // then
        assertThat(result).isSameAs(room);
        verify(customGameParticipantRepository, never()).save(any(CustomGameParticipant.class));
    }

    @Test
    @DisplayName("joinRoom - 정원이 가득 차면 CUSTOM_ROOM_FULL을 던진다")
    void joinRoom_FullRoom_ThrowException() {
        // given
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        given(customGameRoomRepository.findByInviteCodeForUpdate(INVITE_CODE)).willReturn(Optional.of(room));
        given(customGameParticipantRepository.existsByCustomRoomIdAndUserId(ROOM_ID, PLAYER_USER_ID))
                .willReturn(false);
        given(customGameParticipantRepository.countByCustomRoomId(ROOM_ID))
                .willReturn((long) CustomGameRoom.MAX_PARTICIPANTS);

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.joinRoom(INVITE_CODE, PLAYER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_FULL));
        verify(customGameParticipantRepository, never()).save(any(CustomGameParticipant.class));
    }

    @Test
    @DisplayName("joinRoom - 닫힌 room이면 INVALID_STATE를 던진다")
    void joinRoom_ClosedRoom_ThrowException() {
        // given
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        room.close(LocalDateTime.now());
        given(customGameRoomRepository.findByInviteCodeForUpdate(INVITE_CODE)).willReturn(Optional.of(room));

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.joinRoom(INVITE_CODE, PLAYER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE));
    }

    @Test
    @DisplayName("joinRoom - 시작된 room이면 INVALID_STATE를 던진다")
    void joinRoom_StartedRoom_ThrowException() {
        // given
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        room.markStarted(LocalDateTime.now());
        given(customGameRoomRepository.findByInviteCodeForUpdate(INVITE_CODE)).willReturn(Optional.of(room));

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.joinRoom(INVITE_CODE, PLAYER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE));
    }

    @Test
    @DisplayName("joinRoom - blank inviteCode면 INVALID_INVITE_CODE를 던진다")
    void joinRoom_BlankInviteCode_ThrowException() {
        assertThatThrownBy(() -> customGameRoomCommandService.joinRoom(" ", PLAYER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_INVITE_CODE));
    }

    @Test
    @DisplayName("joinRoom - 존재하지 않는 inviteCode면 NOT_FOUND를 던진다")
    void joinRoom_NotFound_ThrowException() {
        // given
        given(customGameRoomRepository.findByInviteCodeForUpdate(INVITE_CODE)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.joinRoom(INVITE_CODE, PLAYER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));
        verify(customGameParticipantRepository, never()).save(any(CustomGameParticipant.class));
    }

    @Test
    @DisplayName("joinRoom - userId가 null이면 INVALID_PARTICIPANT를 던진다")
    void joinRoom_NullUserId_ThrowException() {
        assertThatThrownBy(() -> customGameRoomCommandService.joinRoom(INVITE_CODE, null))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT));
    }

    @Test
    @DisplayName("leaveRoom - 일반 참가자는 participant row를 삭제한다")
    void leaveRoom_PlayerParticipant_DeleteParticipant() {
        // given
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        given(customGameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
        given(customGameParticipantRepository.existsByCustomRoomIdAndUserId(ROOM_ID, PLAYER_USER_ID))
                .willReturn(true);

        // when
        CustomGameRoom result = customGameRoomCommandService.leaveRoom(ROOM_ID, PLAYER_USER_ID);

        // then
        assertThat(result).isSameAs(room);
        assertThat(result.isWaiting()).isTrue();
        verify(customGameParticipantRepository).deleteByCustomRoomIdAndUserId(ROOM_ID, PLAYER_USER_ID);
        verify(customGameParticipantRepository, never()).deleteByCustomRoomId(ROOM_ID);
    }

    @Test
    @DisplayName("leaveRoom - 방장이 나가면 room을 CLOSED로 전환하고 participant를 정리한다")
    void leaveRoom_Owner_CloseRoom() {
        // given
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        given(customGameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
        given(customGameParticipantRepository.existsByCustomRoomIdAndUserId(ROOM_ID, OWNER_USER_ID))
                .willReturn(true);

        // when
        CustomGameRoom result = customGameRoomCommandService.leaveRoom(ROOM_ID, OWNER_USER_ID);

        // then
        assertThat(result).isSameAs(room);
        assertThat(result.isClosed()).isTrue();
        verify(customGameParticipantRepository).deleteByCustomRoomId(ROOM_ID);
        verify(customGameParticipantRepository, never()).deleteByCustomRoomIdAndUserId(ROOM_ID, OWNER_USER_ID);
    }

    @Test
    @DisplayName("leaveRoom - 참가하지 않은 사용자는 INVALID_PARTICIPANT를 던진다")
    void leaveRoom_NotParticipant_ThrowException() {
        // given
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        given(customGameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
        given(customGameParticipantRepository.existsByCustomRoomIdAndUserId(ROOM_ID, PLAYER_USER_ID))
                .willReturn(false);

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.leaveRoom(ROOM_ID, PLAYER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT));
    }

    @Test
    @DisplayName("leaveRoom - 닫힌 room이면 INVALID_STATE를 던진다")
    void leaveRoom_ClosedRoom_ThrowException() {
        // given
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        room.close(LocalDateTime.now());
        given(customGameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.leaveRoom(ROOM_ID, OWNER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE));
    }

    @Test
    @DisplayName("leaveRoom - 시작된 room이면 INVALID_STATE를 던진다")
    void leaveRoom_StartedRoom_ThrowException() {
        // given
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        room.markStarted(LocalDateTime.now());
        given(customGameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.leaveRoom(ROOM_ID, OWNER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE));
    }

    @Test
    @DisplayName("leaveRoom - 존재하지 않는 room이면 NOT_FOUND를 던진다")
    void leaveRoom_NotFound_ThrowException() {
        // given
        given(customGameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.leaveRoom(ROOM_ID, PLAYER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));
        verify(customGameParticipantRepository, never()).deleteByCustomRoomIdAndUserId(ROOM_ID, PLAYER_USER_ID);
    }

    @Test
    @DisplayName("leaveRoom - roomId가 null이면 NOT_FOUND를 던진다")
    void leaveRoom_NullRoomId_ThrowException() {
        assertThatThrownBy(() -> customGameRoomCommandService.leaveRoom(null, PLAYER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("leaveRoom - userId가 null이면 INVALID_PARTICIPANT를 던진다")
    void leaveRoom_NullUserId_ThrowException() {
        assertThatThrownBy(() -> customGameRoomCommandService.leaveRoom(ROOM_ID, null))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT));
    }

    @Test
    @DisplayName("startRoom - 방장이 2명 참가자 WAITING room을 STARTED로 전환하고 참가자 userId를 순서대로 반환한다")
    void startRoom_Success() {
        // given
        LocalDateTime startedAt = LocalDateTime.now();
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        given(customGameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
        given(customGameParticipantRepository.findByCustomRoomIdOrderByIdAsc(ROOM_ID))
                .willReturn(List.of(
                        participant(ROOM_ID, OWNER_USER_ID, CustomRoomParticipantRole.OWNER),
                        participant(ROOM_ID, PLAYER_USER_ID, CustomRoomParticipantRole.PLAYER)
                ));

        // when
        CustomGameRoomStartResult result = customGameRoomCommandService.startRoom(
                ROOM_ID,
                OWNER_USER_ID,
                startedAt
        );

        // then
        assertThat(result.room()).isSameAs(room);
        assertThat(result.room().isStarted()).isTrue();
        assertThat(result.room().getStartedAt()).isEqualTo(startedAt);
        assertThat(result.participantUserIds()).containsExactly(OWNER_USER_ID, PLAYER_USER_ID);
    }

    @Test
    @DisplayName("startRoom - 방장이 아니면 INVALID_PARTICIPANT를 던진다")
    void startRoom_NotOwner_ThrowException() {
        // given
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        given(customGameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.startRoom(
                ROOM_ID,
                PLAYER_USER_ID,
                LocalDateTime.now()
        )).isInstanceOfSatisfying(CoreException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT));
        verify(customGameParticipantRepository, never()).findByCustomRoomIdOrderByIdAsc(ROOM_ID);
    }

    @Test
    @DisplayName("startRoom - 참가자가 1명이면 INCOMPLETE_PARTICIPANTS를 던진다")
    void startRoom_OneParticipant_ThrowException() {
        // given
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        given(customGameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
        given(customGameParticipantRepository.findByCustomRoomIdOrderByIdAsc(ROOM_ID))
                .willReturn(List.of(participant(ROOM_ID, OWNER_USER_ID, CustomRoomParticipantRole.OWNER)));

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.startRoom(
                ROOM_ID,
                OWNER_USER_ID,
                LocalDateTime.now()
        )).isInstanceOfSatisfying(CoreException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.INCOMPLETE_PARTICIPANTS));
        assertThat(room.isWaiting()).isTrue();
    }

    @Test
    @DisplayName("startRoom - 시작된 room이면 INVALID_STATE를 던진다")
    void startRoom_StartedRoom_ThrowException() {
        // given
        CustomGameRoom room = room(ROOM_ID, OWNER_USER_ID);
        room.markStarted(LocalDateTime.now());
        given(customGameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.startRoom(
                ROOM_ID,
                OWNER_USER_ID,
                LocalDateTime.now()
        )).isInstanceOfSatisfying(CoreException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE));
    }

    @Test
    @DisplayName("startRoom - 존재하지 않는 room이면 NOT_FOUND를 던진다")
    void startRoom_NotFound_ThrowException() {
        // given
        given(customGameRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> customGameRoomCommandService.startRoom(
                ROOM_ID,
                OWNER_USER_ID,
                LocalDateTime.now()
        )).isInstanceOfSatisfying(CoreException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("startRoom - roomId가 null이면 NOT_FOUND를 던진다")
    void startRoom_NullRoomId_ThrowException() {
        assertThatThrownBy(() -> customGameRoomCommandService.startRoom(
                null,
                OWNER_USER_ID,
                LocalDateTime.now()
        )).isInstanceOfSatisfying(CoreException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("startRoom - ownerUserId가 null이면 INVALID_PARTICIPANT를 던진다")
    void startRoom_NullOwnerUserId_ThrowException() {
        assertThatThrownBy(() -> customGameRoomCommandService.startRoom(
                ROOM_ID,
                null,
                LocalDateTime.now()
        )).isInstanceOfSatisfying(CoreException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT));
    }

    private CustomGameRoom room(Long roomId, Long ownerUserId) {
        CustomGameRoom room = CustomGameRoom.create(ownerUserId, INVITE_CODE);
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }

    private CustomGameParticipant participant(
            Long roomId,
            Long userId,
            CustomRoomParticipantRole role
    ) {
        return CustomGameParticipant.create(roomId, userId, role);
    }
}
