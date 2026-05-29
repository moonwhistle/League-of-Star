package com.sang.smite.matching.domain.service;

import com.sang.smite.domain.match.domain.MatchSession;
import com.sang.smite.domain.match.domain.MatchStatus;
import com.sang.smite.domain.match.domain.MatchResponseStatus;
import com.sang.smite.domain.match.domain.MatchTicket;
import com.sang.smite.domain.match.event.MatchFoundEvent;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.repository.MatchSessionStore;
import com.sang.smite.matching.repository.MatchTimeoutStore;
import com.sang.smite.matching.repository.MatchUserStatusStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchFoundServiceTest {

    @InjectMocks
    private MatchFoundService matchFoundService;

    @Mock
    private MatchUserStatusStore userStatusStore;

    @Mock
    private MatchSessionStore sessionStore;

    @Mock
    private MatchTimeoutStore timeoutStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Clock clock;

    @Test
    @DisplayName("매칭 성사 시 세션과 timeout을 먼저 준비한 뒤 상태 변경과 이벤트 발행을 수행한다")
    void process() {
        // given
        MatchTicket userA = new MatchTicket(1L, 10, System.currentTimeMillis());
        MatchTicket userB = new MatchTicket(2L, 11, System.currentTimeMillis());
        when(clock.millis()).thenReturn(1_000L);

        // when
        matchFoundService.process(userA, userB);

        // then
        InOrder inOrder = inOrder(sessionStore, timeoutStore, userStatusStore, eventPublisher);
        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        inOrder.verify(sessionStore).save(
                sessionCaptor.capture(),
                eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS)
        );
        MatchSession savedSession = sessionCaptor.getValue();

        assertThat(savedSession.userA()).isEqualTo(1L);
        assertThat(savedSession.userB()).isEqualTo(2L);
        assertThat(savedSession.userATierScore()).isEqualTo(userA.tierScore());
        assertThat(savedSession.userBTierScore()).isEqualTo(userB.tierScore());
        assertThat(savedSession.userAEntryTime()).isEqualTo(userA.entryTime());
        assertThat(savedSession.userBEntryTime()).isEqualTo(userB.entryTime());
        assertThat(savedSession.status()).isEqualTo(MatchStatus.FOUND);
        assertThat(savedSession.matchId()).isNotBlank();
        assertThat(savedSession.userAStatus()).isEqualTo(MatchResponseStatus.PENDING);
        assertThat(savedSession.userBStatus()).isEqualTo(MatchResponseStatus.PENDING);

        inOrder.verify(timeoutStore).addPending(
                savedSession.matchId(),
                1_000L + MatchingConstants.MATCH_RESPONSE_TIMEOUT_SECONDS * 1000L
        );
        inOrder.verify(userStatusStore).updateStatus(1L, MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);
        inOrder.verify(userStatusStore).updateStatus(2L, MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);

        ArgumentCaptor<MatchFoundEvent> eventCaptor = ArgumentCaptor.forClass(MatchFoundEvent.class);
        inOrder.verify(eventPublisher).publishEvent(eventCaptor.capture());
        MatchFoundEvent publishedEvent = eventCaptor.getValue();

        assertThat(publishedEvent.userA()).isEqualTo(1L);
        assertThat(publishedEvent.userB()).isEqualTo(2L);
        assertThat(publishedEvent.matchId()).isEqualTo(savedSession.matchId());
        assertThat(publishedEvent.acceptTimeoutSeconds()).isEqualTo(MatchingConstants.MATCH_RESPONSE_TIMEOUT_SECONDS);
    }
}
