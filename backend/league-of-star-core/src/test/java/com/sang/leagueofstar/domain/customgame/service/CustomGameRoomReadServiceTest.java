package com.sang.leagueofstar.domain.customgame.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameParticipant;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomParticipantRole;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomStatus;
import com.sang.leagueofstar.domain.customgame.repository.CustomGameParticipantRepository;
import com.sang.leagueofstar.domain.customgame.repository.CustomGameRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CustomGameRoomReadServiceTest {

    private static final Long OWNER_USER_ID = 1L;
    private static final String INVITE_CODE = "AB12CD";

    @InjectMocks
    private CustomGameRoomReadService customGameRoomReadService;

    @Mock
    private CustomGameRoomRepository customGameRoomRepository;

    @Mock
    private CustomGameParticipantRepository customGameParticipantRepository;

    @Test
    @DisplayName("getWaitingRoomByInviteCode - WAITING room을 반환한다")
    void getWaitingRoomByInviteCode_ReturnWaitingRoom() {
        // given
        CustomGameRoom customGameRoom = CustomGameRoom.create(OWNER_USER_ID, INVITE_CODE);
        given(customGameRoomRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.of(customGameRoom));

        // when
        CustomGameRoom result = customGameRoomReadService.getWaitingRoomByInviteCode(INVITE_CODE);

        // then
        assertThat(result).isSameAs(customGameRoom);
    }

    @Test
    @DisplayName("getWaitingRoomByInviteCode - inviteCode가 없으면 예외를 던진다")
    void getWaitingRoomByInviteCode_NotFound_ThrowException() {
        // given
        given(customGameRoomRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> customGameRoomReadService.getWaitingRoomByInviteCode(INVITE_CODE))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("getWaitingRoomByInviteCode - inviteCode가 blank면 INVALID_INVITE_CODE를 던진다")
    void getWaitingRoomByInviteCode_BlankInviteCode_ThrowException() {
        assertThatThrownBy(() -> customGameRoomReadService.getWaitingRoomByInviteCode(" "))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_INVITE_CODE));
    }

    @Test
    @DisplayName("getWaitingRoomByInviteCode - STARTED room이면 공개 조회할 수 없다")
    void getWaitingRoomByInviteCode_Started_ThrowException() {
        // given
        CustomGameRoom customGameRoom = CustomGameRoom.create(OWNER_USER_ID, INVITE_CODE);
        customGameRoom.markStarted(LocalDateTime.now());
        given(customGameRoomRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.of(customGameRoom));

        // when & then
        assertThatThrownBy(() -> customGameRoomReadService.getWaitingRoomByInviteCode(INVITE_CODE))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE));
    }

    @Test
    @DisplayName("getWaitingRoomByInviteCode - CLOSED room이면 공개 조회할 수 없다")
    void getWaitingRoomByInviteCode_Closed_ThrowException() {
        // given
        CustomGameRoom customGameRoom = CustomGameRoom.create(OWNER_USER_ID, INVITE_CODE);
        customGameRoom.close(LocalDateTime.now());
        given(customGameRoomRepository.findByInviteCode(INVITE_CODE)).willReturn(Optional.of(customGameRoom));

        // when & then
        assertThatThrownBy(() -> customGameRoomReadService.getWaitingRoomByInviteCode(INVITE_CODE))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE));
    }

    @Test
    @DisplayName("findWaitingRooms - WAITING room 목록을 반환한다")
    void findWaitingRooms_ReturnWaitingRooms() {
        // given
        List<CustomGameRoom> waitingRooms = List.of(CustomGameRoom.create(OWNER_USER_ID, INVITE_CODE));
        given(customGameRoomRepository.findByStatusOrderByIdAsc(CustomRoomStatus.WAITING))
                .willReturn(waitingRooms);

        // when
        List<CustomGameRoom> result = customGameRoomReadService.findWaitingRooms();

        // then
        assertThat(result).containsExactlyElementsOf(waitingRooms);
    }

    @Test
    @DisplayName("getParticipants - customRoomId 기준 participant 목록을 반환한다")
    void getParticipants_ReturnParticipants() {
        // given
        List<CustomGameParticipant> participants = List.of(
                CustomGameParticipant.create(10L, OWNER_USER_ID, CustomRoomParticipantRole.OWNER)
        );
        given(customGameParticipantRepository.findByCustomRoomIdOrderByIdAsc(10L)).willReturn(participants);

        // when
        List<CustomGameParticipant> result = customGameRoomReadService.getParticipants(10L);

        // then
        assertThat(result).containsExactlyElementsOf(participants);
    }

    @Test
    @DisplayName("findParticipantsByRoomIds - roomId 목록 기준 participant 목록을 반환한다")
    void findParticipantsByRoomIds_ReturnParticipants() {
        // given
        List<Long> roomIds = List.of(10L, 11L);
        List<CustomGameParticipant> participants = List.of(
                CustomGameParticipant.create(10L, OWNER_USER_ID, CustomRoomParticipantRole.OWNER),
                CustomGameParticipant.create(11L, 2L, CustomRoomParticipantRole.OWNER)
        );
        given(customGameParticipantRepository.findByCustomRoomIdInOrderByCustomRoomIdAscIdAsc(roomIds))
                .willReturn(participants);

        // when
        List<CustomGameParticipant> result = customGameRoomReadService.findParticipantsByRoomIds(roomIds);

        // then
        assertThat(result).containsExactlyElementsOf(participants);
    }

    @Test
    @DisplayName("findParticipantsByRoomIds - roomId 목록이 비어 있으면 빈 목록을 반환한다")
    void findParticipantsByRoomIds_EmptyRoomIds_ReturnEmptyList() {
        // when
        List<CustomGameParticipant> result = customGameRoomReadService.findParticipantsByRoomIds(List.of());

        // then
        assertThat(result).isEmpty();
    }
}
