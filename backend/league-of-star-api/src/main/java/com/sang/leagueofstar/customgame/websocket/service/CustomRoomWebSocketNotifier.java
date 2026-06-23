package com.sang.leagueofstar.customgame.websocket.service;

import com.sang.leagueofstar.customgame.controller.response.CustomRoomResponse;
import com.sang.leagueofstar.customgame.websocket.dto.CustomRoomWebSocketServerMessage;
import com.sang.leagueofstar.customgame.websocket.session.CustomRoomWebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class CustomRoomWebSocketNotifier {

    private final CustomRoomWebSocketMessageSender messageSender;
    private final CustomRoomWebSocketSessionRegistry sessionRegistry;

    public void notifyRoomUpdatedAfterCommit(CustomRoomResponse response) {
        afterCommit(() -> messageSender.broadcast(
                response.roomId(),
                CustomRoomWebSocketServerMessage.roomUpdated(response)
        ));
    }

    public void notifyParticipantLeftAfterCommit(CustomRoomResponse response, Long userId) {
        afterCommit(() -> {
            sessionRegistry.closeAndUnregister(response.roomId(), userId);
            messageSender.broadcast(
                    response.roomId(),
                    CustomRoomWebSocketServerMessage.roomUpdated(response)
            );
        });
    }

    public void notifyRoomClosedAfterCommit(CustomRoomResponse response) {
        afterCommit(() -> {
            messageSender.broadcast(
                    response.roomId(),
                    CustomRoomWebSocketServerMessage.roomClosed(response)
            );
            sessionRegistry.closeAndUnregisterRoom(response.roomId());
        });
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }

        action.run();
    }
}
