package com.sang.smite.notification.controller;

import com.sang.smite.common.path.notification.NotificationPath;
import com.sang.smite.global.resolver.annotation.AuthUser;
import com.sang.smite.notification.service.MatchNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 매칭 알림 SSE 스트림 API를 제공합니다.
 */
@RestController
@RequestMapping(NotificationPath.NOTIFICATION_BASE)
@RequiredArgsConstructor
public class MatchNotificationController {

    private final MatchNotificationService matchNotificationService;

    /**
     * 매칭 성사 알림을 받기 위한 SSE 스트림을 연결합니다.
     *
     * @return text/event-stream 응답 스트림
     */
    @GetMapping(value = NotificationPath.MATCH_STREAM, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthUser Long userId) {
        return matchNotificationService.connect(userId);
    }
}
