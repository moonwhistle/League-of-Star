package com.sang.leagueofstar.customgame.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomListResponse;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomResponse;
import com.sang.leagueofstar.customgame.websocket.service.CustomRoomWebSocketNotifier;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameParticipant;
import com.sang.leagueofstar.domain.customgame.domain.CustomGameRoom;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomParticipantRole;
import com.sang.leagueofstar.domain.customgame.service.CustomGameRoomCommandService;
import com.sang.leagueofstar.domain.customgame.service.CustomGameRoomReadService;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class CustomGameRoomServiceTest {

    private static final Long OWNER_USER_ID = 1L;
    private static final Long PLAYER_USER_ID = 2L;

    @InjectMocks
    private CustomGameRoomService customGameRoomService;

    @Mock
    private CustomGameRoomCommandService customGameRoomCommandService;

    @Mock
    private CustomGameRoomReadService customGameRoomReadService;

    @Mock
    private UserReadService userReadService;

    @Mock
    private CustomRoomWebSocketNotifier customRoomWebSocketNotifier;

    @Test
    @DisplayName("createRoom - core command 결과와 participant 조회 결과를 조합해 room response를 만든다")
    void createRoom_ReturnRoomResponse() {
        // given
        CustomGameRoom room = room(100L, OWNER_USER_ID, "AB12CD");
        List<CustomGameParticipant> participants = List.of(participant(100L, OWNER_USER_ID, CustomRoomParticipantRole.OWNER));
        given(customGameRoomCommandService.createRoom(OWNER_USER_ID)).willReturn(room);
        given(customGameRoomReadService.getParticipants(100L)).willReturn(participants);
        given(userReadService.findAllByIdsOrThrow(anyCollection())).willReturn(List.of(user(OWNER_USER_ID, "Host")));

        // when
        CustomRoomResponse response = customGameRoomService.createRoom(OWNER_USER_ID);

        // then
        assertThat(response.roomId()).isEqualTo(100L);
        assertThat(response.roomName()).isEqualTo("Host's room");
        assertThat(response.inviteCode()).isEqualTo("AB12CD");
        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().get(0).nickname()).isEqualTo("Host");
        assertThat(response.participants().get(0).role()).isEqualTo("OWNER");
        then(customGameRoomCommandService).should().createRoom(OWNER_USER_ID);
        then(customGameRoomReadService).should().getParticipants(100L);
    }

    @Test
    @DisplayName("getPublicRooms - WAITING room 목록과 participant count를 batch 조회로 조립한다")
    void getPublicRooms_ReturnPublicRoomList() {
        // given
        CustomGameRoom firstRoom = room(100L, OWNER_USER_ID, "AB12CD");
        CustomGameRoom secondRoom = room(101L, PLAYER_USER_ID, "EF34GH");
        given(customGameRoomReadService.findWaitingRooms()).willReturn(List.of(firstRoom, secondRoom));
        given(customGameRoomReadService.findParticipantsByRoomIds(anyCollection())).willReturn(List.of(
                participant(100L, OWNER_USER_ID, CustomRoomParticipantRole.OWNER),
                participant(100L, PLAYER_USER_ID, CustomRoomParticipantRole.PLAYER),
                participant(101L, PLAYER_USER_ID, CustomRoomParticipantRole.OWNER)
        ));
        given(userReadService.findAllByIdsOrThrow(anyCollection()))
                .willReturn(List.of(user(OWNER_USER_ID, "Host"), user(PLAYER_USER_ID, "Guest")));

        // when
        CustomRoomListResponse response = customGameRoomService.getPublicRooms();

        // then
        assertThat(response.rooms()).hasSize(2);
        assertThat(response.rooms().get(0).roomName()).isEqualTo("Host's room");
        assertThat(response.rooms().get(0).currentParticipants()).isEqualTo(2);
        assertThat(response.rooms().get(1).roomName()).isEqualTo("Guest's room");
        assertThat(response.rooms().get(1).currentParticipants()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> roomIdsCaptor =
                (ArgumentCaptor<Collection<Long>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Collection.class);
        then(customGameRoomReadService).should().findParticipantsByRoomIds(roomIdsCaptor.capture());
        assertThat(roomIdsCaptor.getValue()).containsExactly(100L, 101L);
        then(userReadService).should(times(1)).findAllByIdsOrThrow(anyCollection());
        then(userReadService).should(never()).findById(OWNER_USER_ID);
        then(userReadService).should(never()).findById(PLAYER_USER_ID);
    }

    @Test
    @DisplayName("getInvitePreview - inviteCode 조회 결과와 participant nickname을 조립한다")
    void getInvitePreview_ReturnRoomResponse() {
        // given
        CustomGameRoom room = room(100L, OWNER_USER_ID, "AB12CD");
        given(customGameRoomReadService.getWaitingRoomByInviteCode("AB12CD")).willReturn(room);
        given(customGameRoomReadService.getParticipants(100L)).willReturn(List.of(
                participant(100L, OWNER_USER_ID, CustomRoomParticipantRole.OWNER),
                participant(100L, PLAYER_USER_ID, CustomRoomParticipantRole.PLAYER)
        ));
        given(userReadService.findAllByIdsOrThrow(anyCollection()))
                .willReturn(List.of(user(OWNER_USER_ID, "Host"), user(PLAYER_USER_ID, "Guest")));

        // when
        CustomRoomResponse response = customGameRoomService.getInvitePreview("AB12CD");

        // then
        assertThat(response.roomId()).isEqualTo(100L);
        assertThat(response.roomName()).isEqualTo("Host's room");
        assertThat(response.participants()).extracting("nickname")
                .containsExactly("Host", "Guest");
    }

    @Test
    @DisplayName("getInvitePreview - inviteCode가 없으면 core 예외를 전달한다")
    void getInvitePreview_NotFound_ThrowException() {
        // given
        given(customGameRoomReadService.getWaitingRoomByInviteCode("NONE"))
                .willThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> customGameRoomService.getInvitePreview("NONE"))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_NOT_FOUND));
        then(customGameRoomReadService).should(never()).getParticipants(100L);
    }

    @Test
    @DisplayName("getWaitingRoom - roomId 조회 결과와 participant nickname을 조립한다")
    void getWaitingRoom_ReturnRoomResponse() {
        // given
        CustomGameRoom room = room(100L, OWNER_USER_ID, "AB12CD");
        given(customGameRoomReadService.getWaitingRoom(100L)).willReturn(room);
        given(customGameRoomReadService.getParticipants(100L)).willReturn(List.of(
                participant(100L, OWNER_USER_ID, CustomRoomParticipantRole.OWNER),
                participant(100L, PLAYER_USER_ID, CustomRoomParticipantRole.PLAYER)
        ));
        given(userReadService.findAllByIdsOrThrow(anyCollection()))
                .willReturn(List.of(user(OWNER_USER_ID, "Host"), user(PLAYER_USER_ID, "Guest")));

        // when
        CustomRoomResponse response = customGameRoomService.getWaitingRoom(100L);

        // then
        assertThat(response.roomId()).isEqualTo(100L);
        assertThat(response.roomName()).isEqualTo("Host's room");
        assertThat(response.participants()).extracting("nickname")
                .containsExactly("Host", "Guest");
        then(customGameRoomReadService).should().getWaitingRoom(100L);
        then(customGameRoomReadService).should().getParticipants(100L);
    }

    @Test
    @DisplayName("joinRoom - core command 이후 현재 participant를 다시 조회해 room response를 만든다")
    void joinRoom_ReturnRoomResponse() {
        // given
        CustomGameRoom room = room(100L, OWNER_USER_ID, "AB12CD");
        given(customGameRoomCommandService.joinRoom("AB12CD", PLAYER_USER_ID)).willReturn(room);
        given(customGameRoomReadService.getParticipants(100L)).willReturn(List.of(
                participant(100L, OWNER_USER_ID, CustomRoomParticipantRole.OWNER),
                participant(100L, PLAYER_USER_ID, CustomRoomParticipantRole.PLAYER)
        ));
        given(userReadService.findAllByIdsOrThrow(anyCollection()))
                .willReturn(List.of(user(OWNER_USER_ID, "Host"), user(PLAYER_USER_ID, "Guest")));

        // when
        CustomRoomResponse response = customGameRoomService.joinRoom("AB12CD", PLAYER_USER_ID);

        // then
        assertThat(response.roomId()).isEqualTo(100L);
        assertThat(response.roomName()).isEqualTo("Host's room");
        assertThat(response.participants()).extracting("nickname")
                .containsExactly("Host", "Guest");
        assertThat(response.participants()).extracting("role")
                .containsExactly("OWNER", "PLAYER");
        then(customGameRoomCommandService).should().joinRoom("AB12CD", PLAYER_USER_ID);
        then(customGameRoomReadService).should().getParticipants(100L);
        then(customRoomWebSocketNotifier).should().notifyRoomUpdatedAfterCommit(response);
        then(userReadService).should(times(1)).findAllByIdsOrThrow(anyCollection());
        then(userReadService).should(never()).findById(OWNER_USER_ID);
        then(userReadService).should(never()).findById(PLAYER_USER_ID);
    }

    @Test
    @DisplayName("joinRoom - core command 예외가 발생하면 participant를 조회하지 않는다")
    void joinRoom_CoreException_ThrowException() {
        // given
        given(customGameRoomCommandService.joinRoom("AB12CD", PLAYER_USER_ID))
                .willThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_FULL));

        // when & then
        assertThatThrownBy(() -> customGameRoomService.joinRoom("AB12CD", PLAYER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_FULL));
        then(customGameRoomReadService).should(never()).getParticipants(100L);
        then(customRoomWebSocketNotifier).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("leaveRoom - core command 이후 현재 participant를 다시 조회해 room response를 만든다")
    void leaveRoom_ReturnRoomResponse() {
        // given
        CustomGameRoom room = room(100L, OWNER_USER_ID, "AB12CD");
        given(customGameRoomCommandService.leaveRoom(100L, PLAYER_USER_ID)).willReturn(room);
        given(customGameRoomReadService.getParticipants(100L)).willReturn(List.of(
                participant(100L, OWNER_USER_ID, CustomRoomParticipantRole.OWNER)
        ));
        given(userReadService.findAllByIdsOrThrow(anyCollection()))
                .willReturn(List.of(user(OWNER_USER_ID, "Host")));

        // when
        CustomRoomResponse response = customGameRoomService.leaveRoom(100L, PLAYER_USER_ID);

        // then
        assertThat(response.roomId()).isEqualTo(100L);
        assertThat(response.roomName()).isEqualTo("Host's room");
        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().get(0).nickname()).isEqualTo("Host");
        then(customGameRoomCommandService).should().leaveRoom(100L, PLAYER_USER_ID);
        then(customGameRoomReadService).should().getParticipants(100L);
        then(customRoomWebSocketNotifier).should().notifyParticipantLeftAfterCommit(response, PLAYER_USER_ID);
    }

    @Test
    @DisplayName("leaveRoom - 방장이 나가 room이 닫히면 ROOM_CLOSED broadcast를 예약한다")
    void leaveRoom_OwnerClosedRoom_NotifyRoomClosed() {
        // given
        CustomGameRoom room = room(100L, OWNER_USER_ID, "AB12CD");
        room.close(java.time.LocalDateTime.now());
        given(customGameRoomCommandService.leaveRoom(100L, OWNER_USER_ID)).willReturn(room);
        given(customGameRoomReadService.getParticipants(100L)).willReturn(List.of());
        given(userReadService.findAllByIdsOrThrow(anyCollection()))
                .willReturn(List.of(user(OWNER_USER_ID, "Host")));

        // when
        CustomRoomResponse response = customGameRoomService.leaveRoom(100L, OWNER_USER_ID);

        // then
        assertThat(response.roomId()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(response.participants()).isEmpty();
        then(customRoomWebSocketNotifier).should().notifyRoomClosedAfterCommit(response);
    }

    @Test
    @DisplayName("leaveRoom - core command 예외가 발생하면 participant를 조회하지 않는다")
    void leaveRoom_CoreException_ThrowException() {
        // given
        given(customGameRoomCommandService.leaveRoom(100L, PLAYER_USER_ID))
                .willThrow(new CoreException(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT));

        // when & then
        assertThatThrownBy(() -> customGameRoomService.leaveRoom(100L, PLAYER_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT));
        then(customGameRoomReadService).should(never()).getParticipants(100L);
        then(customRoomWebSocketNotifier).shouldHaveNoInteractions();
    }

    private CustomGameRoom room(Long id, Long ownerUserId, String inviteCode) {
        CustomGameRoom room = CustomGameRoom.create(ownerUserId, inviteCode);
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }

    private CustomGameParticipant participant(Long roomId, Long userId, CustomRoomParticipantRole role) {
        return CustomGameParticipant.create(roomId, userId, role);
    }

    private User user(Long id, String nickname) {
        return User.builder()
                .id(id)
                .email("user" + id + "@example.com")
                .nickname(nickname)
                .build();
    }
}
