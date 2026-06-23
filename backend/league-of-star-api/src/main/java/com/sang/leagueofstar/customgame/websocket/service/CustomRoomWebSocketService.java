package com.sang.leagueofstar.customgame.websocket.service;

import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.customgame.controller.response.CustomRoomResponse;
import com.sang.leagueofstar.customgame.service.CustomGameRoomService;
import com.sang.leagueofstar.customgame.websocket.dto.CustomRoomWebSocketServerMessage;
import com.sang.leagueofstar.customgame.websocket.session.CustomRoomWebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomRoomWebSocketService {

    private final CustomRoomWebSocketSessionRegistry sessionRegistry;
    private final CustomGameRoomService customGameRoomService;
    private final CustomRoomWebSocketMessageSender messageSender;

    public void registerSession(Long customRoomId, Long userId, WebSocketSession session) {
        sessionRegistry.register(customRoomId, userId, session);

        try {
            CustomRoomResponse snapshot = customGameRoomService.getWaitingRoom(customRoomId);
            messageSender.send(session, CustomRoomWebSocketServerMessage.roomUpdated(snapshot));
        } catch (CoreException e) {
            sessionRegistry.unregister(session.getId());
            closePolicyViolation(session);
            log.warn("Failed to load custom room snapshot. customRoomId={}, userId={}",
                    customRoomId, userId, e);
        }
    }

    public void handleUnsupportedClientMessage(WebSocketSession session) {
        messageSender.send(session, CustomRoomWebSocketServerMessage.invalidMessageType());
    }

    public void cleanupSession(WebSocketSession session) {
        sessionRegistry.unregister(session.getId());
    }

    private void closePolicyViolation(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (IOException e) {
            log.warn("Failed to close invalid custom room WebSocket session. sessionId={}", session.getId(), e);
        }
    }
}
