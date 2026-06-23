package com.sang.leagueofstar.customgame.websocket.service;

import com.sang.leagueofstar.customgame.controller.response.CustomRoomResponse;
import com.sang.leagueofstar.customgame.websocket.dto.CustomRoomWebSocketMessageType;
import com.sang.leagueofstar.customgame.websocket.dto.CustomRoomWebSocketServerMessage;
import com.sang.leagueofstar.customgame.websocket.session.CustomRoomWebSocketSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CustomRoomWebSocketNotifierTest {

    private static final Long CUSTOM_ROOM_ID = 100L;
    private static final Long USER_ID = 2L;

    private final CustomRoomWebSocketMessageSender messageSender = mock(CustomRoomWebSocketMessageSender.class);
    private final CustomRoomWebSocketSessionRegistry sessionRegistry = mock(CustomRoomWebSocketSessionRegistry.class);
    private final CustomRoomWebSocketNotifier notifier = new CustomRoomWebSocketNotifier(
            messageSender,
            sessionRegistry
    );

    @Test
    @DisplayName("notifyRoomUpdatedAfterCommit - transaction이 없으면 즉시 ROOM_UPDATED를 broadcast한다")
    void notifyRoomUpdatedAfterCommit_NoTransaction_BroadcastImmediately() {
        // given
        CustomRoomResponse response = roomResponse("WAITING");

        // when
        notifier.notifyRoomUpdatedAfterCommit(response);

        // then
        CustomRoomWebSocketServerMessage message = captureBroadcastMessage();
        assertThat(message.type()).isEqualTo(CustomRoomWebSocketMessageType.ROOM_UPDATED);
        assertThat(message.payload()).isSameAs(response);
    }

    @Test
    @DisplayName("notifyRoomUpdatedAfterCommit - transaction이 있으면 afterCommit에 ROOM_UPDATED를 broadcast한다")
    void notifyRoomUpdatedAfterCommit_ActiveTransaction_BroadcastAfterCommit() {
        // given
        CustomRoomResponse response = roomResponse("WAITING");
        TransactionSynchronizationManager.initSynchronization();
        try {
            // when
            notifier.notifyRoomUpdatedAfterCommit(response);

            // then
            verify(messageSender, never()).broadcast(
                    org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.any()
            );
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            synchronizations.get(0).afterCommit();
            CustomRoomWebSocketServerMessage message = captureBroadcastMessage();
            assertThat(message.type()).isEqualTo(CustomRoomWebSocketMessageType.ROOM_UPDATED);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("notifyParticipantLeftAfterCommit - 떠난 user session을 먼저 닫고 남은 참가자에게 ROOM_UPDATED를 broadcast한다")
    void notifyParticipantLeftAfterCommit_BroadcastAndCloseUserSession() {
        // given
        CustomRoomResponse response = roomResponse("WAITING");

        // when
        notifier.notifyParticipantLeftAfterCommit(response, USER_ID);

        // then
        InOrder inOrder = inOrder(messageSender, sessionRegistry);
        inOrder.verify(sessionRegistry).closeAndUnregister(CUSTOM_ROOM_ID, USER_ID);
        inOrder.verify(messageSender).broadcast(
                org.mockito.ArgumentMatchers.eq(CUSTOM_ROOM_ID),
                org.mockito.ArgumentMatchers.argThat(message ->
                        message.type() == CustomRoomWebSocketMessageType.ROOM_UPDATED
                )
        );
    }

    @Test
    @DisplayName("notifyRoomClosedAfterCommit - ROOM_CLOSED broadcast 이후 room 전체 session을 닫는다")
    void notifyRoomClosedAfterCommit_BroadcastAndCloseRoomSessions() {
        // given
        CustomRoomResponse response = roomResponse("CLOSED");

        // when
        notifier.notifyRoomClosedAfterCommit(response);

        // then
        InOrder inOrder = inOrder(messageSender, sessionRegistry);
        inOrder.verify(messageSender).broadcast(
                org.mockito.ArgumentMatchers.eq(CUSTOM_ROOM_ID),
                org.mockito.ArgumentMatchers.argThat(message ->
                        message.type() == CustomRoomWebSocketMessageType.ROOM_CLOSED
                )
        );
        inOrder.verify(sessionRegistry).closeAndUnregisterRoom(CUSTOM_ROOM_ID);
    }

    private CustomRoomWebSocketServerMessage captureBroadcastMessage() {
        ArgumentCaptor<CustomRoomWebSocketServerMessage> messageCaptor =
                ArgumentCaptor.forClass(CustomRoomWebSocketServerMessage.class);
        verify(messageSender).broadcast(org.mockito.ArgumentMatchers.eq(CUSTOM_ROOM_ID), messageCaptor.capture());
        return messageCaptor.getValue();
    }

    private CustomRoomResponse roomResponse(String status) {
        return new CustomRoomResponse(
                CUSTOM_ROOM_ID,
                "Host's room",
                "AB12CD",
                1L,
                status,
                2,
                List.of()
        );
    }
}
