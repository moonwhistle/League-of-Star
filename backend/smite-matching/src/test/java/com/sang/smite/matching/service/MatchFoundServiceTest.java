package com.sang.smite.matching.service;

import com.sang.smite.domain.match.domain.MatchSession;
import com.sang.smite.domain.match.domain.MatchStatus;
import com.sang.smite.domain.match.domain.MatchTicket;
import com.sang.smite.domain.match.event.MatchFoundEvent;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.repository.MatchSessionStore;
import com.sang.smite.matching.repository.MatchUserStatusStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchFoundServiceTest {

    @InjectMocks
    private MatchFoundService matchFoundService;

    @Mock
    private MatchUserStatusStore userStatusStore;

    @Mock
    private MatchSessionStore sessionStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("매칭 성사 시 상태 변경, 세션 생성, 이벤트 발행이 모두 정상 수행된다")
    void process() {
        // given
        MatchTicket userA = new MatchTicket(1L, 10, System.currentTimeMillis());
        MatchTicket userB = new MatchTicket(2L, 11, System.currentTimeMillis());

        // when
        matchFoundService.process(userA, userB);

        // then
        // 1. 유저 상태 변경 검증
        verify(userStatusStore).updateStatus(1L, MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore).updateStatus(2L, MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);

        // 2. 세션 생성 검증
        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(12L)); // TTL 12초 확인
        MatchSession savedSession = sessionCaptor.getValue();
        
        assertThat(savedSession.userA()).isEqualTo(1L);
        assertThat(savedSession.userB()).isEqualTo(2L);
        assertThat(savedSession.status()).isEqualTo(MatchStatus.FOUND);
        assertThat(savedSession.matchId()).isNotBlank();

        // 3. 이벤트 발행 검증
        ArgumentCaptor<MatchFoundEvent> eventCaptor = ArgumentCaptor.forClass(MatchFoundEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        MatchFoundEvent publishedEvent = eventCaptor.getValue();
        
        assertThat(publishedEvent.userA()).isEqualTo(1L);
        assertThat(publishedEvent.userB()).isEqualTo(2L);
        assertThat(publishedEvent.matchId()).isEqualTo(savedSession.matchId());
        assertThat(publishedEvent.acceptTimeoutSeconds()).isEqualTo(10);
    }
}
