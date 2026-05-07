package com.sang.smite.notification.metrics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * SSE 매칭 알림 성능 비교에 사용하는 메트릭 이름 및 태그 상수입니다.
 *
 * <p>MVC SseEmitter와 향후 WebFlux/Netty SSE 구현을 같은 기준으로 비교하기 위해
 * 구현체와 무관한 이름을 사용합니다.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SseNotificationMetricNames {

    /** 현재 활성 SSE 연결 수 (Gauge) -> sse_notification_connections_active */
    public static final String CONNECTIONS_ACTIVE = "sse.notification.connections.active";

    /** SSE 연결 등록 횟수 (Counter) -> sse_notification_connections_opened_total */
    public static final String CONNECTIONS_OPENED = "sse.notification.connections.opened";

    /** SSE 연결 종료 횟수 (Counter) -> sse_notification_connections_closed_total */
    public static final String CONNECTIONS_CLOSED = "sse.notification.connections.closed";

    /** SSE 연결 유지 시간 (Timer) -> sse_notification_connection_duration_seconds */
    public static final String CONNECTION_DURATION = "sse.notification.connection.duration";

    /** SSE 이벤트 전송 시도 횟수 (Counter) -> sse_notification_events_send_attempts_total */
    public static final String EVENTS_SEND_ATTEMPTS = "sse.notification.events.send.attempts";

    /** SSE 이벤트 전송 성공 횟수 (Counter) -> sse_notification_events_send_success_total */
    public static final String EVENTS_SEND_SUCCESS = "sse.notification.events.send.success";

    /** SSE 이벤트 전송 실패 횟수 (Counter) -> sse_notification_events_send_failures_total */
    public static final String EVENTS_SEND_FAILURES = "sse.notification.events.send.failures";

    /** SSE 이벤트 전송 소요 시간 (Timer) -> sse_notification_event_send_duration_seconds */
    public static final String EVENT_SEND_DURATION = "sse.notification.event.send.duration";

    /** Pub/Sub publish 성공 횟수 (Counter) -> sse_notification_pubsub_publish_success_total */
    public static final String PUBSUB_PUBLISH_SUCCESS = "sse.notification.pubsub.publish.success";

    /** Pub/Sub publish 실패 횟수 (Counter) -> sse_notification_pubsub_publish_failures_total */
    public static final String PUBSUB_PUBLISH_FAILURES = "sse.notification.pubsub.publish.failures";

    /** Pub/Sub 메시지 수신 횟수 (Counter) -> sse_notification_pubsub_messages_received_total */
    public static final String PUBSUB_MESSAGES_RECEIVED = "sse.notification.pubsub.messages.received";

    /** Pub/Sub 메시지 처리 실패 횟수 (Counter) -> sse_notification_pubsub_messages_failures_total */
    public static final String PUBSUB_MESSAGES_FAILURES = "sse.notification.pubsub.messages.failures";

    /** match_found 메시지 대상 유저가 현재 인스턴스에 연결되어 있던 횟수 (Counter) -> sse_notification_match_found_dispatch_local_hits_total */
    public static final String MATCH_FOUND_DISPATCH_LOCAL_HITS = "sse.notification.match_found.dispatch.local.hits";

    /** match_found 메시지 대상 유저가 현재 인스턴스에 연결되어 있지 않던 횟수 (Counter) -> sse_notification_match_found_dispatch_local_misses_total */
    public static final String MATCH_FOUND_DISPATCH_LOCAL_MISSES = "sse.notification.match_found.dispatch.local.misses";

    public static final String TAG_EVENT = "event";
    public static final String TAG_REASON = "reason";
    public static final String TAG_RESULT = "result";

    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILURE = "failure";
    public static final String REASON_DECODE = "decode";
    public static final String REASON_DISPATCH = "dispatch";
    public static final String REASON_COMPLETION = "completion";
    public static final String REASON_TIMEOUT = "timeout";
    public static final String REASON_ERROR = "error";
    public static final String REASON_REPLACED = "replaced";
    public static final String REASON_SEND_FAILURE = "send_failure";
}
