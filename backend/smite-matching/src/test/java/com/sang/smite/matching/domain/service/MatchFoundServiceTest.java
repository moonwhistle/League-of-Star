package com.sang.smite.matching.domain.service;

import com.sang.smite.domain.match.domain.MatchSession;
import com.sang.smite.domain.match.domain.MatchStatus;
import com.sang.smite.domain.match.domain.MatchResponseStatus;
import com.sang.smite.domain.match.domain.MatchTicket;
import com.sang.smite.domain.match.event.MatchFoundEvent;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.repository.MatchQueueStore;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchFoundServiceTest {

    @InjectMocks
    private MatchFoundService matchFoundService;

    @Mock
    private MatchQueueStore matchQueueStore;

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
        verifyNoInteractions(matchQueueStore);
    }

    @Test
    @DisplayName("세션 저장에 실패하면 두 유저를 기존 티켓으로 queue에 복귀시키고 MATCHING 상태로 복구한다")
    void processWhenSessionSaveFails() {
        // given
        MatchTicket userA = new MatchTicket(1L, 10, System.currentTimeMillis());
        MatchTicket userB = new MatchTicket(2L, 11, System.currentTimeMillis());
        RuntimeException cause = new RuntimeException("session save failed");
        doThrow(cause).when(sessionStore).save(any(MatchSession.class), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));

        // when & then
        assertThatThrownBy(() -> matchFoundService.process(userA, userB))
                .isSameAs(cause);

        verify(matchQueueStore).add(userA);
        verify(matchQueueStore).add(userB);
        verify(userStatusStore).updateStatus(1L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore).updateStatus(2L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(sessionStore, never()).delete(any());
        verify(timeoutStore, never()).addPending(any(), anyLong());
        verify(timeoutStore, never()).cleanup(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(userStatusStore, never()).updateStatus(1L, MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore, never()).updateStatus(2L, MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);
    }

    @Test
    @DisplayName("세션 저장 실패 보상 중 한 유저 queue 복귀가 실패해도 나머지 보상을 계속 시도한다")
    void processWhenSessionSaveFailsAndQueueRestorePartiallyFails() {
        // given
        MatchTicket userA = new MatchTicket(1L, 10, System.currentTimeMillis());
        MatchTicket userB = new MatchTicket(2L, 11, System.currentTimeMillis());
        RuntimeException cause = new RuntimeException("session save failed");
        doThrow(cause).when(sessionStore).save(any(MatchSession.class), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        doThrow(new RuntimeException("queue restore failed")).when(matchQueueStore).add(userA);

        // when & then
        assertThatThrownBy(() -> matchFoundService.process(userA, userB))
                .isSameAs(cause);

        verify(matchQueueStore).add(userA);
        verify(matchQueueStore).add(userB);
        verify(userStatusStore).updateStatus(1L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore).updateStatus(2L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
    }

    @Test
    @DisplayName("세션 저장 실패 보상 중 한 유저 status 복구가 실패해도 나머지 보상을 계속 시도한다")
    void processWhenSessionSaveFailsAndStatusRestorePartiallyFails() {
        // given
        MatchTicket userA = new MatchTicket(1L, 10, System.currentTimeMillis());
        MatchTicket userB = new MatchTicket(2L, 11, System.currentTimeMillis());
        RuntimeException cause = new RuntimeException("session save failed");
        doThrow(cause).when(sessionStore).save(any(MatchSession.class), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        doThrow(new RuntimeException("status restore failed"))
                .when(userStatusStore)
                .updateStatus(1L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);

        // when & then
        assertThatThrownBy(() -> matchFoundService.process(userA, userB))
                .isSameAs(cause);

        verify(matchQueueStore).add(userA);
        verify(matchQueueStore).add(userB);
        verify(userStatusStore).updateStatus(1L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore).updateStatus(2L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
    }

    @Test
    @DisplayName("timeout pending 등록에 실패하면 저장된 세션을 삭제하고 두 유저를 queue와 MATCHING 상태로 복구한다")
    void processWhenTimeoutPendingFails() {
        // given
        MatchTicket userA = new MatchTicket(1L, 10, System.currentTimeMillis());
        MatchTicket userB = new MatchTicket(2L, 11, System.currentTimeMillis());
        RuntimeException cause = new RuntimeException("timeout pending failed");
        when(clock.millis()).thenReturn(1_000L);
        doThrow(cause).when(timeoutStore).addPending(any(), anyLong());

        // when & then
        assertThatThrownBy(() -> matchFoundService.process(userA, userB))
                .isSameAs(cause);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        MatchSession savedSession = sessionCaptor.getValue();

        verify(timeoutStore).addPending(
                savedSession.matchId(),
                1_000L + MatchingConstants.MATCH_RESPONSE_TIMEOUT_SECONDS * 1000L
        );
        verify(sessionStore).delete(savedSession.matchId());
        verify(matchQueueStore).add(userA);
        verify(matchQueueStore).add(userB);
        verify(userStatusStore).updateStatus(1L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore).updateStatus(2L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(timeoutStore, never()).cleanup(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(userStatusStore, never()).updateStatus(1L, MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore, never()).updateStatus(2L, MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);
    }

    @Test
    @DisplayName("timeout pending 등록 실패 보상 중 세션 삭제가 실패해도 queue 복귀와 status 복구를 계속 시도한다")
    void processWhenTimeoutPendingFailsAndSessionDeleteFails() {
        // given
        MatchTicket userA = new MatchTicket(1L, 10, System.currentTimeMillis());
        MatchTicket userB = new MatchTicket(2L, 11, System.currentTimeMillis());
        RuntimeException cause = new RuntimeException("timeout pending failed");
        when(clock.millis()).thenReturn(1_000L);
        doThrow(cause).when(timeoutStore).addPending(any(), anyLong());
        doThrow(new RuntimeException("session delete failed")).when(sessionStore).delete(any());

        // when & then
        assertThatThrownBy(() -> matchFoundService.process(userA, userB))
                .isSameAs(cause);

        verify(sessionStore).delete(any());
        verify(matchQueueStore).add(userA);
        verify(matchQueueStore).add(userB);
        verify(userStatusStore).updateStatus(1L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore).updateStatus(2L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("userA FOUND 갱신에 실패하면 timeout과 세션을 정리하고 두 유저를 queue와 MATCHING 상태로 복구한다")
    void processWhenUserAFoundStatusFails() {
        // given
        MatchTicket userA = new MatchTicket(1L, 10, System.currentTimeMillis());
        MatchTicket userB = new MatchTicket(2L, 11, System.currentTimeMillis());
        RuntimeException cause = new RuntimeException("userA found failed");
        when(clock.millis()).thenReturn(1_000L);
        doThrow(cause)
                .when(userStatusStore)
                .updateStatus(1L, MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);

        // when & then
        assertThatThrownBy(() -> matchFoundService.process(userA, userB))
                .isSameAs(cause);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        MatchSession savedSession = sessionCaptor.getValue();

        verify(timeoutStore).cleanup(savedSession.matchId());
        verify(sessionStore).delete(savedSession.matchId());
        verify(matchQueueStore).add(userA);
        verify(matchQueueStore).add(userB);
        verify(userStatusStore).updateStatus(1L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore).updateStatus(2L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore, never()).updateStatus(2L, MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("userB FOUND 갱신에 실패하면 userA까지 포함해 두 유저를 queue와 MATCHING 상태로 복구한다")
    void processWhenUserBFoundStatusFails() {
        // given
        MatchTicket userA = new MatchTicket(1L, 10, System.currentTimeMillis());
        MatchTicket userB = new MatchTicket(2L, 11, System.currentTimeMillis());
        RuntimeException cause = new RuntimeException("userB found failed");
        when(clock.millis()).thenReturn(1_000L);
        doAnswer(invocation -> {
            Long userId = invocation.getArgument(0, Long.class);
            MatchStatus status = invocation.getArgument(1, MatchStatus.class);
            if (userId.equals(2L) && status == MatchStatus.FOUND) {
                throw cause;
            }
            return null;
        })
                .when(userStatusStore)
                .updateStatus(any(Long.class), any(MatchStatus.class), anyLong());

        // when & then
        assertThatThrownBy(() -> matchFoundService.process(userA, userB))
                .isSameAs(cause);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        MatchSession savedSession = sessionCaptor.getValue();

        verify(userStatusStore).updateStatus(1L, MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);
        verify(timeoutStore).cleanup(savedSession.matchId());
        verify(sessionStore).delete(savedSession.matchId());
        verify(matchQueueStore).add(userA);
        verify(matchQueueStore).add(userB);
        verify(userStatusStore).updateStatus(1L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore).updateStatus(2L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("FOUND 갱신 실패 보상 중 timeout cleanup과 세션 삭제가 실패해도 queue 복귀와 status 복구를 계속 시도한다")
    void processWhenFoundStatusFailsAndCleanupDeleteFail() {
        // given
        MatchTicket userA = new MatchTicket(1L, 10, System.currentTimeMillis());
        MatchTicket userB = new MatchTicket(2L, 11, System.currentTimeMillis());
        RuntimeException cause = new RuntimeException("userA found failed");
        when(clock.millis()).thenReturn(1_000L);
        doThrow(cause)
                .when(userStatusStore)
                .updateStatus(1L, MatchStatus.FOUND, MatchingConstants.STATUS_TTL_SECONDS);
        doThrow(new RuntimeException("timeout cleanup failed")).when(timeoutStore).cleanup(any());
        doThrow(new RuntimeException("session delete failed")).when(sessionStore).delete(any());

        // when & then
        assertThatThrownBy(() -> matchFoundService.process(userA, userB))
                .isSameAs(cause);

        verify(timeoutStore).cleanup(any());
        verify(sessionStore).delete(any());
        verify(matchQueueStore).add(userA);
        verify(matchQueueStore).add(userB);
        verify(userStatusStore).updateStatus(1L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore).updateStatus(2L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
