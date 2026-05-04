package com.sang.smite.notification.listener;

import com.sang.smite.domain.match.event.MatchFoundEvent;
import com.sang.smite.notification.constants.MatchNotificationEventName;
import com.sang.smite.notification.domain.SseConnection;
import com.sang.smite.notification.dto.MatchFoundNotification;
import com.sang.smite.notification.metrics.SseNotificationMetrics;
import com.sang.smite.notification.service.SseConnectionRegistry;
import com.sang.smite.notification.service.SseNotificationSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchFoundEventListenerTest {

    private final SseConnectionRegistry registry = new SseConnectionRegistry(SseNotificationMetrics.noop());
    private final SseNotificationSender sender = new SseNotificationSender(registry, SseNotificationMetrics.noop());
    private final MatchFoundEventListener listener = new MatchFoundEventListener(registry, sender);

    @Test
    @DisplayName("MatchFoundEvent 발생 시 두 유저에게 match_found 이벤트를 전송한다.")
    void sendMatchFoundToBothUsers() throws IOException {
        // given
        SseEmitter userAEmitter = mock(SseEmitter.class);
        SseEmitter userBEmitter = mock(SseEmitter.class);
        registry.register(new SseConnection(1L, userAEmitter));
        registry.register(new SseConnection(2L, userBEmitter));

        // when
        listener.handle(new MatchFoundEvent("match-1", 1L, 2L, 10));

        // then
        verify(userAEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(userBEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("한 유저만 연결되어 있으면 연결된 유저에게만 match_found 이벤트를 전송한다.")
    void sendOnlyConnectedUser() throws IOException {
        // given
        SseEmitter userAEmitter = mock(SseEmitter.class);
        registry.register(new SseConnection(1L, userAEmitter));

        // when
        listener.handle(new MatchFoundEvent("match-1", 1L, 2L, 10));

        // then
        verify(userAEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("두 유저 모두 연결되어 있지 않아도 예외 없이 종료한다.")
    void skipWhenUsersAreNotConnected() {
        // when
        listener.handle(new MatchFoundEvent("match-1", 1L, 2L, 10));

        // then
        assertThat(registry.count()).isZero();
    }

    @Test
    @DisplayName("match_found 전송 실패 시 실패 연결을 제거한다.")
    void removeConnectionWhenSendFails() throws IOException {
        // given
        SseEmitter userAEmitter = mock(SseEmitter.class);
        IOException exception = new IOException("disconnected");
        doThrow(exception).when(userAEmitter).send(any(SseEmitter.SseEventBuilder.class));
        registry.register(new SseConnection(1L, userAEmitter));

        // when
        listener.handle(new MatchFoundEvent("match-1", 1L, 2L, 10));

        // then
        assertThat(registry.findByUserId(1L)).isEmpty();
        verify(userAEmitter).completeWithError(exception);
    }

    @Test
    @DisplayName("match_found payload는 대상 유저 기준으로 상대 유저 정보를 포함한다.")
    void sendTargetUserPayload() {
        // given
        SseConnectionRegistry registry = new SseConnectionRegistry(SseNotificationMetrics.noop());
        SseNotificationSender sender = mock(SseNotificationSender.class);
        MatchFoundEventListener listener = new MatchFoundEventListener(registry, sender);
        SseConnection userAConnection = new SseConnection(1L, mock(SseEmitter.class));
        SseConnection userBConnection = new SseConnection(2L, mock(SseEmitter.class));

        registry.register(userAConnection);
        registry.register(userBConnection);
        when(sender.send(any(SseConnection.class), any(String.class), any())).thenReturn(true);

        // when
        listener.handle(new MatchFoundEvent("match-1", 1L, 2L, 10));

        // then
        ArgumentCaptor<MatchFoundNotification> notificationCaptor = ArgumentCaptor.forClass(MatchFoundNotification.class);
        verify(sender).send(
                eq(userAConnection),
                eq(MatchNotificationEventName.MATCH_FOUND),
                notificationCaptor.capture()
        );

        MatchFoundNotification notification = notificationCaptor.getValue();
        assertThat(notification.matchId()).isEqualTo("match-1");
        assertThat(notification.userId()).isEqualTo(1L);
        assertThat(notification.opponentUserId()).isEqualTo(2L);
        assertThat(notification.acceptTimeoutSeconds()).isEqualTo(10);
        assertThat(notification.eventCreatedAt()).isNotNull();
    }
}
