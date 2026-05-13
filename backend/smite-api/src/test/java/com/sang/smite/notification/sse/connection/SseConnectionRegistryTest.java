package com.sang.smite.notification.sse.connection;

import com.sang.smite.notification.sse.metrics.SseNotificationMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SseConnectionRegistryTest {

    private final SseConnectionRegistry registry = new SseConnectionRegistry(SseNotificationMetrics.noop());

    @Test
    @DisplayName("유저별 SSE 연결을 등록하고 조회한다.")
    void registerAndFind() {
        // given
        Long userId = 1L;
        SseConnection connection = new SseConnection(userId, mock(SseEmitter.class));

        // when
        registry.register(connection);

        // then
        assertThat(registry.findByUserId(userId)).contains(connection);
        assertThat(registry.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 유저가 재연결하면 기존 연결을 종료하고 새 연결로 교체한다.")
    void replacePreviousConnection() {
        // given
        Long userId = 1L;
        SseEmitter previousEmitter = mock(SseEmitter.class);
        SseConnection previousConnection = new SseConnection(userId, previousEmitter);
        SseConnection newConnection = new SseConnection(userId, mock(SseEmitter.class));

        registry.register(previousConnection);

        // when
        registry.register(newConnection);

        // then
        verify(previousEmitter).complete();
        assertThat(registry.findByUserId(userId)).contains(newConnection);
        assertThat(registry.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 연결과 다른 오래된 연결 제거 요청은 새 연결을 제거하지 않는다.")
    void removeIgnoresStaleConnection() {
        // given
        Long userId = 1L;
        SseConnection staleConnection = new SseConnection(userId, mock(SseEmitter.class));
        SseConnection currentConnection = new SseConnection(userId, mock(SseEmitter.class));
        registry.register(currentConnection);

        // when
        registry.remove(userId, staleConnection);

        // then
        assertThat(registry.findByUserId(userId)).contains(currentConnection);
        assertThat(registry.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 연결 제거 요청이면 저장소에서 제거한다.")
    void removeCurrentConnection() {
        // given
        Long userId = 1L;
        SseConnection connection = new SseConnection(userId, mock(SseEmitter.class));
        registry.register(connection);

        // when
        registry.remove(userId, connection);

        // then
        assertThat(registry.findByUserId(userId)).isEmpty();
        assertThat(registry.count()).isZero();
    }

    @Test
    @DisplayName("연결 등록 시 종료/타임아웃/에러 콜백을 등록한다.")
    void registerCallbacks() {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        SseConnection connection = new SseConnection(1L, emitter);

        // when
        registry.register(connection);

        // then
        verify(emitter).onCompletion(any(Runnable.class));
        verify(emitter).onTimeout(any(Runnable.class));
        verify(emitter).onError(any());
    }

    @Test
    @DisplayName("SSE 연결 완료 콜백이 실행되면 현재 연결을 제거한다.")
    void removeConnectionOnCompletion() {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(0);
            callback.run();
            return null;
        }).when(emitter).onCompletion(any(Runnable.class));

        SseConnection connection = new SseConnection(1L, emitter);

        // when
        registry.register(connection);

        // then
        assertThat(registry.findByUserId(1L)).isEmpty();
        assertThat(registry.count()).isZero();
    }

    @Test
    @DisplayName("SSE 타임아웃 콜백이 실행되면 현재 연결을 제거하고 완료 처리한다.")
    void removeConnectionOnTimeout() {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(0);
            callback.run();
            return null;
        }).when(emitter).onTimeout(any(Runnable.class));

        SseConnection connection = new SseConnection(1L, emitter);

        // when
        registry.register(connection);

        // then
        assertThat(registry.findByUserId(1L)).isEmpty();
        assertThat(registry.count()).isZero();
        verify(emitter).complete();
    }

    @Test
    @DisplayName("SSE 에러 콜백이 실행되면 현재 연결을 제거한다.")
    void removeConnectionOnError() {
        // given
        SseEmitter emitter = mock(SseEmitter.class);
        doAnswer(invocation -> {
            java.util.function.Consumer<Throwable> callback = invocation.getArgument(0);
            callback.accept(new RuntimeException("connection error"));
            return null;
        }).when(emitter).onError(any());

        SseConnection connection = new SseConnection(1L, emitter);

        // when
        registry.register(connection);

        // then
        assertThat(registry.findByUserId(1L)).isEmpty();
        assertThat(registry.count()).isZero();
    }
}
