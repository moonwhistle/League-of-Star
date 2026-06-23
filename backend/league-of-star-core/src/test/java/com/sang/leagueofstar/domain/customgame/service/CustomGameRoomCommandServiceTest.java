package com.sang.leagueofstar.domain.customgame.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameParticipant;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomParticipantRole;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomStatus;
import com.sang.leagueofstar.domain.customgame.repository.CustomGameParticipantRepository;
import com.sang.leagueofstar.domain.customgame.repository.CustomGameRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomGameRoomCommandServiceTest {

    private static final Long OWNER_USER_ID = 1L;
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
        given(customGameRoomRepository.save(any(CustomGameRoom.class)))
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
        verify(customGameRoomRepository).save(result);
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
        given(customGameRoomRepository.save(any(CustomGameRoom.class)))
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
}
