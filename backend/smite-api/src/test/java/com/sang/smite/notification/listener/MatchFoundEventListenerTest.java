package com.sang.smite.notification.listener;

import com.sang.smite.domain.match.event.MatchFoundEvent;
import com.sang.smite.notification.domain.SseConnection;
import com.sang.smite.notification.service.SseConnectionRegistry;
import com.sang.smite.notification.service.SseNotificationSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MatchFoundEventListenerTest {

    private final SseConnectionRegistry registry = new SseConnectionRegistry();
    private final SseNotificationSender sender = new SseNotificationSender(registry);
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
}
