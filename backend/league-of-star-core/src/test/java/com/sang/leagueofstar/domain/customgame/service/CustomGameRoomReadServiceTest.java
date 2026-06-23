package com.sang.leagueofstar.domain.customgame.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomStatus;
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
}
