package com.sang.smite.game.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sang.smite.game.end.service.GameEndScheduleService;
import com.sang.smite.game.rtt.domain.GameRttPongResult;
import com.sang.smite.game.rtt.service.GameRttMeasurementService;
import com.sang.smite.game.smite.service.GameSmiteService;
import com.sang.smite.game.start.domain.GameStartBlockedReason;
import com.sang.smite.game.start.domain.GameStartFailureReason;
import com.sang.smite.game.start.domain.GameStartTransitionResult;
import com.sang.smite.game.start.dto.GameStartScenarioPayload;
import com.sang.smite.game.start.service.GameStartFailureProcessor;
import com.sang.smite.game.start.service.GameStartScenarioService;
import com.sang.smite.game.start.service.GameStartTransitionService;
import com.sang.smite.game.websocket.dto.GameWebSocketMessageType;
import com.sang.smite.game.websocket.service.GameRoomWebSocketMessageSender;
import com.sang.smite.game.websocket.service.GameStartWebSocketSender;
import com.sang.smite.game.websocket.service.GameWaitingWebSocketService;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSession;
import com.sang.smite.game.websocket.session.GameRoomWebSocketSessionRegistry;
import com.sang.smite.game.websocket.session.GameWebSocketSessionAttribute;
import com.sang.smite.game.waiting.domain.GameWaitingReadyResult;
import com.sang.smite.game.waiting.service.GameWaitingReadyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameWaitingWebSocketHandlerTest {

    private static final Long GAME_ROOM_ID = 100L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final String FIRST_SESSION_ID = "session-1";
    private static final String SECOND_SESSION_ID = "session-2";
    private static final Instant SERVER_RECEIVE_TIME = Instant.parse("2026-05-21T03:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(SERVER_RECEIVE_TIME, ZoneOffset.UTC);
    private final GameRoomWebSocketSessionRegistry sessionRegistry = new GameRoomWebSocketSessionRegistry();
    private final GameWaitingReadyService gameWaitingReadyService = mock(GameWaitingReadyService.class);
    private final GameRttMeasurementService gameRttMeasurementService = mock(GameRttMeasurementService.class);
    private final GameStartScenarioService gameStartScenarioService = mock(GameStartScenarioService.class);
    private final GameStartTransitionService gameStartTransitionService = mock(GameStartTransitionService.class);
    private final GameStartFailureProcessor gameStartFailureProcessor = mock(GameStartFailureProcessor.class);
    private final GameEndScheduleService gameEndScheduleService = mock(GameEndScheduleService.class);
    private final GameStartWebSocketSender gameStartWebSocketSender = mock(GameStartWebSocketSender.class);
    private final GameSmiteService gameSmiteService = mock(GameSmiteService.class);
    private final GameRoomWebSocketMessageSender messageSender = new GameRoomWebSocketMessageSender(
            objectMapper,
            sessionRegistry
    );
    private final GameWaitingWebSocketService gameWaitingWebSocketService = new GameWaitingWebSocketService(
            sessionRegistry,
            gameWaitingReadyService,
            gameRttMeasurementService,
            messageSender,
            gameStartScenarioService,
            gameStartTransitionService,
            gameStartFailureProcessor,
            gameEndScheduleService,
            gameStartWebSocketSender,
            gameSmiteService
    );
    private final GameWaitingWebSocketHandler handler = new GameWaitingWebSocketHandler(
            objectMapper,
            sessionRegistry,
            gameWaitingWebSocketService,
            messageSender,
            clock
    );

    @Test
    @DisplayName("afterConnectionEstablished - session attributes 기준으로 registry 등록 후 PLAYER_JOINED를 전송한다")
    void afterConnectionEstablished_RegisterAndBroadcastJoined() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);

        // when
        handler.afterConnectionEstablished(session);

        // then
        assertThat(sessionRegistry.isConnected(GAME_ROOM_ID, FIRST_USER_ID)).isTrue();
        JsonNode message = lastSentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.PLAYER_JOINED.name());
        assertThat(message.get("payload").get("userId").asLong()).isEqualTo(FIRST_USER_ID);
    }

    @Test
    @DisplayName("afterConnectionEstablished - session attributes가 없으면 연결을 종료한다")
    void afterConnectionEstablished_MissingAttributes_Close() throws Exception {
        // given
        WebSocketSession session = sessionWithoutAttributes(FIRST_SESSION_ID);

        // when
        handler.afterConnectionEstablished(session);

        // then
        assertThat(sessionRegistry.findBySessionId(FIRST_SESSION_ID)).isEmpty();
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("handleTextMessage - CLIENT_READY 수신 시 READY 상태를 저장하고 PLAYER_READY를 broadcast한다")
    void handleTextMessage_ClientReady() throws Exception {
        // given
        WebSocketSession firstSession = session(FIRST_SESSION_ID, FIRST_USER_ID);
        WebSocketSession secondSession = session(SECOND_SESSION_ID, SECOND_USER_ID);
        handler.afterConnectionEstablished(firstSession);
        handler.afterConnectionEstablished(secondSession);
        clearInvocations(firstSession, secondSession);
        when(gameWaitingReadyService.markReady(GAME_ROOM_ID, FIRST_USER_ID))
                .thenReturn(GameWaitingReadyResult.accepted(false));

        // when
        handler.handleTextMessage(firstSession, new TextMessage("{\"type\":\"CLIENT_READY\",\"payload\":{}}"));

        // then
        assertThat(sessionRegistry.areBothReady(GAME_ROOM_ID)).isFalse();
        JsonNode firstMessage = lastSentMessage(firstSession);
        JsonNode secondMessage = lastSentMessage(secondSession);
        assertThat(firstMessage.get("type").asText()).isEqualTo(GameWebSocketMessageType.PLAYER_READY.name());
        assertThat(secondMessage.get("type").asText()).isEqualTo(GameWebSocketMessageType.PLAYER_READY.name());
        assertThat(firstMessage.get("payload").get("userId").asLong()).isEqualTo(FIRST_USER_ID);
        assertThat(firstMessage.get("payload").get("bothReady").asBoolean()).isFalse();
        verify(gameRttMeasurementService, never()).startMeasurement(eq(GAME_ROOM_ID), anyList());
    }

    @Test
    @DisplayName("handleTextMessage - 양쪽 CLIENT_READY 수신 시 PLAYER_READY 이후 RTT_PING을 전송한다")
    void handleTextMessage_BothReady() throws Exception {
        // given
        WebSocketSession firstSession = session(FIRST_SESSION_ID, FIRST_USER_ID);
        WebSocketSession secondSession = session(SECOND_SESSION_ID, SECOND_USER_ID);
        handler.afterConnectionEstablished(firstSession);
        handler.afterConnectionEstablished(secondSession);
        when(gameWaitingReadyService.markReady(GAME_ROOM_ID, FIRST_USER_ID))
                .thenReturn(GameWaitingReadyResult.accepted(false));
        handler.handleTextMessage(firstSession, new TextMessage("{\"type\":\"CLIENT_READY\",\"payload\":{}}"));
        clearInvocations(firstSession, secondSession);
        when(gameWaitingReadyService.markReady(GAME_ROOM_ID, SECOND_USER_ID))
                .thenReturn(GameWaitingReadyResult.accepted(true));
        when(gameRttMeasurementService.startMeasurement(eq(GAME_ROOM_ID), anyList())).thenReturn(true);

        // when
        handler.handleTextMessage(secondSession, new TextMessage("{\"type\":\"CLIENT_READY\",\"payload\":{}}"));

        // then
        assertThat(sessionRegistry.areBothReady(GAME_ROOM_ID)).isTrue();
        List<JsonNode> firstMessages = sentMessages(firstSession);
        assertThat(firstMessages).extracting(message -> message.get("type").asText())
                .containsExactly(
                        GameWebSocketMessageType.PLAYER_READY.name(),
                        GameWebSocketMessageType.RTT_PING.name()
                );
        assertThat(firstMessages.get(0).get("payload").get("userId").asLong()).isEqualTo(SECOND_USER_ID);
        assertThat(firstMessages.get(0).get("payload").get("bothReady").asBoolean()).isTrue();
        assertThat(firstMessages.get(1).get("payload").get("seq").asInt()).isEqualTo(1);
        verify(gameRttMeasurementService).startMeasurement(eq(GAME_ROOM_ID), anyList());
        verify(gameRttMeasurementService).recordPingSent(GAME_ROOM_ID, FIRST_USER_ID, 1);
        verify(gameRttMeasurementService).recordPingSent(GAME_ROOM_ID, SECOND_USER_ID, 1);
    }

    @Test
    @DisplayName("handleTextMessage - RTT_PONG은 처리 단계 전까지 ERROR로 응답하지 않는다")
    void handleTextMessage_RttPong_NoError() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        when(gameRttMeasurementService.recordPong(GAME_ROOM_ID, FIRST_USER_ID, 1))
                .thenReturn(GameRttPongResult.rejected());

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"RTT_PONG\",\"payload\":{\"seq\":1}}"));

        // then
        verify(session, never()).sendMessage(any());
        verify(gameWaitingReadyService, never()).markReady(GAME_ROOM_ID, FIRST_USER_ID);
    }

    @Test
    @DisplayName("handleTextMessage - SMITE 수신 시 서버 수신 시각을 기록하고 SMITE service로 위임한다")
    void handleTextMessage_Smite_DelegateWithServerReceiveTime() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"SMITE\",\"payload\":{}}"));

        // then
        verify(gameSmiteService).handleSmite(
                GAME_ROOM_ID,
                FIRST_USER_ID,
                SERVER_RECEIVE_TIME.toEpochMilli()
        );
        verify(session, never()).sendMessage(any());
    }

    @Test
    @DisplayName("handleTextMessage - RTT_PONG 처리 후 완료 전이면 다음 RTT_PING을 전송한다")
    void handleTextMessage_RttPong_SendNextPing() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        when(gameRttMeasurementService.recordPong(GAME_ROOM_ID, FIRST_USER_ID, 1))
                .thenReturn(GameRttPongResult.recorded(1));

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"RTT_PONG\",\"payload\":{\"seq\":1}}"));

        // then
        JsonNode message = lastSentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.RTT_PING.name());
        assertThat(message.get("payload").get("seq").asInt()).isEqualTo(2);
        verify(gameRttMeasurementService).recordPingSent(GAME_ROOM_ID, FIRST_USER_ID, 2);
    }

    @Test
    @DisplayName("handleTextMessage - RTT가 완료되고 시작 전환에 성공하면 COUNTDOWN과 GAME_START 전송을 요청한다")
    void handleTextMessage_RttPong_StartGame() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        GameStartScenarioPayload scenario = scenario();
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        when(gameRttMeasurementService.recordPong(GAME_ROOM_ID, FIRST_USER_ID, 5))
                .thenReturn(GameRttPongResult.completed(true, 5));
        when(gameStartScenarioService.getScenarioPayload(GAME_ROOM_ID)).thenReturn(scenario);
        when(gameStartTransitionService.transitionToInProgress(GAME_ROOM_ID))
                .thenReturn(GameStartTransitionResult.started(1000L, 5000L));
        when(gameStartWebSocketSender.sendStart(GAME_ROOM_ID, 1000L, 5000L, scenario)).thenReturn(true);

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"RTT_PONG\",\"payload\":{\"seq\":5}}"));

        // then
        var inOrder = inOrder(gameEndScheduleService, gameStartWebSocketSender);
        inOrder.verify(gameEndScheduleService).registerEndDeadline(GAME_ROOM_ID, 5000L, scenario.durationMs());
        inOrder.verify(gameStartWebSocketSender).sendStart(GAME_ROOM_ID, 1000L, 5000L, scenario);
    }

    @Test
    @DisplayName("handleTextMessage - RTT가 완료되어도 시작 전환이 실패하면 시작 메시지를 보내지 않는다")
    void handleTextMessage_RttPong_StartBlocked() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        when(gameRttMeasurementService.recordPong(GAME_ROOM_ID, FIRST_USER_ID, 5))
                .thenReturn(GameRttPongResult.completed(true, 5));
        when(gameStartTransitionService.transitionToInProgress(GAME_ROOM_ID))
                .thenReturn(GameStartTransitionResult.blocked(GameStartBlockedReason.RTT_NOT_READY));

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"RTT_PONG\",\"payload\":{\"seq\":5}}"));

        // then
        verify(gameStartScenarioService, never()).getScenarioPayload(GAME_ROOM_ID);
        verify(gameEndScheduleService, never()).registerEndDeadline(any(), anyLong(), anyLong());
        verify(gameStartWebSocketSender, never()).sendStart(any(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("handleTextMessage - scenario 조회에 실패하면 시작 실패 processor에 위임하고 시작 메시지를 보내지 않는다")
    void handleTextMessage_RttPong_ScenarioLoadFailed_ProcessStartedFailure() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        when(gameRttMeasurementService.recordPong(GAME_ROOM_ID, FIRST_USER_ID, 5))
                .thenReturn(GameRttPongResult.completed(true, 5));
        when(gameStartTransitionService.transitionToInProgress(GAME_ROOM_ID))
                .thenReturn(GameStartTransitionResult.started(1000L, 5000L));
        when(gameStartScenarioService.getScenarioPayload(GAME_ROOM_ID))
                .thenThrow(new IllegalStateException("scenario missing"));

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"RTT_PONG\",\"payload\":{\"seq\":5}}"));

        // then
        verify(gameStartFailureProcessor).processStartedFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.SCENARIO_LOAD_FAILED,
                false
        );
        verify(gameEndScheduleService, never()).registerEndDeadline(any(), anyLong(), anyLong());
        verify(gameStartWebSocketSender, never()).sendStart(any(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("handleTextMessage - 종료 deadline 등록에 실패하면 시작 실패 processor에 위임하고 시작 메시지를 보내지 않는다")
    void handleTextMessage_RttPong_DeadlineRegistrationFailed_ProcessStartedFailure() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        GameStartScenarioPayload scenario = scenario();
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        when(gameRttMeasurementService.recordPong(GAME_ROOM_ID, FIRST_USER_ID, 5))
                .thenReturn(GameRttPongResult.completed(true, 5));
        when(gameStartScenarioService.getScenarioPayload(GAME_ROOM_ID)).thenReturn(scenario);
        when(gameStartTransitionService.transitionToInProgress(GAME_ROOM_ID))
                .thenReturn(GameStartTransitionResult.started(1000L, 5000L));
        doThrow(new IllegalStateException("redis down"))
                .when(gameEndScheduleService)
                .registerEndDeadline(GAME_ROOM_ID, 5000L, scenario.durationMs());

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"RTT_PONG\",\"payload\":{\"seq\":5}}"));

        // then
        verify(gameStartFailureProcessor).processStartedFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.GAME_END_DEADLINE_REGISTRATION_FAILED,
                true
        );
        verify(gameStartWebSocketSender, never()).sendStart(any(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("handleTextMessage - GAME_START 메시지 전송에 실패하면 등록된 deadline cleanup까지 실패 processor에 위임한다")
    void handleTextMessage_RttPong_SendStartFailed_ProcessStartedFailure() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        GameStartScenarioPayload scenario = scenario();
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        when(gameRttMeasurementService.recordPong(GAME_ROOM_ID, FIRST_USER_ID, 5))
                .thenReturn(GameRttPongResult.completed(true, 5));
        when(gameStartTransitionService.transitionToInProgress(GAME_ROOM_ID))
                .thenReturn(GameStartTransitionResult.started(1000L, 5000L));
        when(gameStartScenarioService.getScenarioPayload(GAME_ROOM_ID)).thenReturn(scenario);
        when(gameStartWebSocketSender.sendStart(GAME_ROOM_ID, 1000L, 5000L, scenario)).thenReturn(false);

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"RTT_PONG\",\"payload\":{\"seq\":5}}"));

        // then
        verify(gameEndScheduleService).registerEndDeadline(GAME_ROOM_ID, 5000L, scenario.durationMs());
        verify(gameStartFailureProcessor).processStartedFailure(
                GAME_ROOM_ID,
                GameStartFailureReason.GAME_START_MESSAGE_SEND_FAILED,
                true
        );
    }

    @Test
    @DisplayName("handleTextMessage - server-only type을 클라이언트가 보내면 ERROR를 응답한다")
    void handleTextMessage_ServerOnlyType_ReturnError() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"PLAYER_READY\",\"payload\":{}}"));

        // then
        JsonNode message = lastSentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.ERROR.name());
        assertThat(message.get("payload").get("code").asText()).isEqualTo("INVALID_MESSAGE_TYPE");
        assertThat(sessionRegistry.areBothReady(GAME_ROOM_ID)).isFalse();
    }

    @Test
    @DisplayName("handleTextMessage - payload의 userId/gameRoomId를 신뢰하지 않고 session attributes를 사용한다")
    void handleTextMessage_IgnorePayloadIdentity() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        when(gameWaitingReadyService.markReady(GAME_ROOM_ID, FIRST_USER_ID))
                .thenReturn(GameWaitingReadyResult.accepted(false));

        // when
        handler.handleTextMessage(
                session,
                new TextMessage("{\"type\":\"CLIENT_READY\",\"payload\":{\"gameRoomId\":999,\"userId\":999}}")
        );

        // then
        JsonNode message = lastSentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.PLAYER_READY.name());
        assertThat(message.get("payload").get("userId").asLong()).isEqualTo(FIRST_USER_ID);
        assertThat(sessionRegistry.findByRoomAndUser(GAME_ROOM_ID, FIRST_USER_ID))
                .get()
                .matches(GameRoomWebSocketSession::isReady);
        assertThat(sessionRegistry.findByRoomAndUser(999L, 999L)).isEmpty();
    }

    @Test
    @DisplayName("handleTextMessage - Redis waiting 상태가 없으면 READY 처리 없이 연결을 종료한다")
    void handleTextMessage_WaitingStateRejected_Close() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        when(gameWaitingReadyService.markReady(GAME_ROOM_ID, FIRST_USER_ID))
                .thenReturn(GameWaitingReadyResult.rejected());

        // when
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"CLIENT_READY\",\"payload\":{}}"));

        // then
        assertThat(sessionRegistry.findBySessionId(FIRST_SESSION_ID)).isEmpty();
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("handleTextMessage - JSON 파싱 실패 시 ERROR를 응답한다")
    void handleTextMessage_InvalidJson_ReturnError() throws Exception {
        // given
        WebSocketSession session = session(FIRST_SESSION_ID, FIRST_USER_ID);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);

        // when
        handler.handleTextMessage(session, new TextMessage("{invalid-json"));

        // then
        JsonNode message = lastSentMessage(session);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.ERROR.name());
        assertThat(message.get("payload").get("code").asText()).isEqualTo("INVALID_MESSAGE_TYPE");
    }

    @Test
    @DisplayName("afterConnectionClosed - registry에서 제거하고 남은 참가자에게 PLAYER_LEFT를 전송한다")
    void afterConnectionClosed_UnregisterAndBroadcastLeft() throws Exception {
        // given
        WebSocketSession firstSession = session(FIRST_SESSION_ID, FIRST_USER_ID);
        WebSocketSession secondSession = session(SECOND_SESSION_ID, SECOND_USER_ID);
        handler.afterConnectionEstablished(firstSession);
        handler.afterConnectionEstablished(secondSession);
        clearInvocations(firstSession, secondSession);

        // when
        handler.afterConnectionClosed(firstSession, CloseStatus.NORMAL);

        // then
        assertThat(sessionRegistry.isConnected(GAME_ROOM_ID, FIRST_USER_ID)).isFalse();
        assertThat(sessionRegistry.isConnected(GAME_ROOM_ID, SECOND_USER_ID)).isTrue();
        JsonNode message = lastSentMessage(secondSession);
        assertThat(message.get("type").asText()).isEqualTo(GameWebSocketMessageType.PLAYER_LEFT.name());
        assertThat(message.get("payload").get("userId").asLong()).isEqualTo(FIRST_USER_ID);
        verify(gameRttMeasurementService).failMeasurement(GAME_ROOM_ID, FIRST_USER_ID);
    }

    private WebSocketSession session(String sessionId, Long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(attributes(userId));
        return session;
    }

    private GameStartScenarioPayload scenario() {
        return new GameStartScenarioPayload(
                10000,
                1000L,
                List.of(new GameStartScenarioPayload.HpTimelineStep(0L, 10000))
        );
    }

    private WebSocketSession sessionWithoutAttributes(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        return session;
    }

    private Map<String, Object> attributes(Long userId) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GameWebSocketSessionAttribute.GAME_ROOM_ID, GAME_ROOM_ID);
        attributes.put(GameWebSocketSessionAttribute.USER_ID, userId);
        return attributes;
    }

    private JsonNode lastSentMessage(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(messageCaptor.capture());
        TextMessage lastMessage = messageCaptor.getAllValues().get(messageCaptor.getAllValues().size() - 1);
        return objectMapper.readTree(lastMessage.getPayload());
    }

    private List<JsonNode> sentMessages(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(2)).sendMessage(messageCaptor.capture());
        return messageCaptor.getAllValues().stream()
                .map(TextMessage::getPayload)
                .map(this::readTree)
                .toList();
    }

    private JsonNode readTree(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
