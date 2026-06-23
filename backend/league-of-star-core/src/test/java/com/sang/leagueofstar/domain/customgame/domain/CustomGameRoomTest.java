package com.sang.leagueofstar.domain.customgame.domain;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomParticipantRole;
import com.sang.leagueofstar.domain.customgame.domain.vo.CustomRoomStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomGameRoomTest {

    @Test
    @DisplayName("create - WAITING custom room을 생성한다")
    void create() {
        // when
        CustomGameRoom room = CustomGameRoom.create(1L, "AB12CD");

        // then
        assertThat(room.getOwnerUserId()).isEqualTo(1L);
        assertThat(room.getWaitingOwnerUserId()).isEqualTo(1L);
        assertThat(room.getInviteCode()).isEqualTo("AB12CD");
        assertThat(room.getStatus()).isEqualTo(CustomRoomStatus.WAITING);
        assertThat(room.isWaiting()).isTrue();
    }

    @Test
    @DisplayName("create - ownerUserId가 null이면 예외를 던진다")
    void create_NullOwnerUserId_ThrowException() {
        assertThatThrownBy(() -> CustomGameRoom.create(null, "AB12CD"))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_PARTICIPANT));
    }

    @Test
    @DisplayName("create - inviteCode가 blank면 예외를 던진다")
    void create_BlankInviteCode_ThrowException() {
        assertThatThrownBy(() -> CustomGameRoom.create(1L, " "))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_INVITE_CODE));
    }

    @Test
    @DisplayName("markStarted - WAITING room을 STARTED로 전환한다")
    void markStarted() {
        // given
        CustomGameRoom room = CustomGameRoom.create(1L, "AB12CD");
        LocalDateTime startedAt = LocalDateTime.now();

        // when
        room.markStarted(startedAt);

        // then
        assertThat(room.isStarted()).isTrue();
        assertThat(room.getWaitingOwnerUserId()).isNull();
        assertThat(room.getStartedAt()).isEqualTo(startedAt);
    }

    @Test
    @DisplayName("close - WAITING unique 대상에서 room을 제거한다")
    void close() {
        // given
        CustomGameRoom room = CustomGameRoom.create(1L, "AB12CD");
        LocalDateTime closedAt = LocalDateTime.now();

        // when
        room.close(closedAt);

        // then
        assertThat(room.isClosed()).isTrue();
        assertThat(room.getWaitingOwnerUserId()).isNull();
        assertThat(room.getClosedAt()).isEqualTo(closedAt);
    }

    @Test
    @DisplayName("markStarted - WAITING이 아니면 예외를 던진다")
    void markStarted_NotWaiting_ThrowException() {
        // given
        CustomGameRoom room = CustomGameRoom.create(1L, "AB12CD");
        room.close(LocalDateTime.now());

        // when & then
        assertThatThrownBy(() -> room.markStarted(LocalDateTime.now()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE));
    }

    @Test
    @DisplayName("participant create - customRoomId 간접참조 participant를 생성한다")
    void createParticipant() {
        // when
        CustomGameParticipant participant = CustomGameParticipant.create(100L, 1L, CustomRoomParticipantRole.OWNER);

        // then
        assertThat(participant.getCustomRoomId()).isEqualTo(100L);
        assertThat(participant.getUserId()).isEqualTo(1L);
        assertThat(participant.getRole()).isEqualTo(CustomRoomParticipantRole.OWNER);
    }

    @Test
    @DisplayName("participant create - customRoomId가 null이면 예외를 던진다")
    void createParticipant_NullCustomRoomId_ThrowException() {
        assertThatThrownBy(() -> CustomGameParticipant.create(null, 1L, CustomRoomParticipantRole.OWNER))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.CUSTOM_ROOM_INVALID_STATE));
    }
}
