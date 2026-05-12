package com.sang.smite.matching.domain.service;

import com.sang.smite.domain.match.domain.MatchSession;
import com.sang.smite.domain.match.domain.MatchStatus;
import com.sang.smite.domain.match.domain.MatchResponseStatus;
import com.sang.smite.domain.match.domain.MatchTicket;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
import com.sang.smite.matching.domain.event.MatchResponseResultEvent;
import com.sang.smite.matching.domain.event.MatchResponseResultEventPublisher;
import com.sang.smite.matching.metrics.MatchResponseMetrics;
import com.sang.smite.matching.repository.MatchSessionStore;
import com.sang.smite.matching.repository.MatchQueueStore;
import com.sang.smite.matching.repository.MatchTimeoutStore;
import com.sang.smite.matching.repository.MatchUserStatusStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchResponseResultServiceTest {

    @InjectMocks
    private MatchResponseResultService processor;

    @Mock
    private MatchSessionStore sessionStore;

    @Mock
    private MatchUserStatusStore userStatusStore;

    @Mock
    private MatchQueueStore matchStore;

    @Mock
    private MatchTimeoutStore timeoutStore;

    @Mock
    private MatchResponseMetrics matchResponseMetrics;

    @Mock
    private MatchResponseResultEventPublisher settlementEventPublisher;

    @Test
    @DisplayName("수락 요청 시 해당 유저의 수락 상태와 유저 상태를 갱신한다")
    void accept() {
        MatchSession session = foundSession();
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.acceptWithLock("match-1", 1L);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        MatchSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.userAStatus()).isEqualTo(MatchResponseStatus.ACCEPTED);
        assertThat(savedSession.userBStatus()).isEqualTo(MatchResponseStatus.PENDING);
        assertThat(savedSession.status()).isEqualTo(MatchStatus.FOUND);
        verify(userStatusStore).updateStatus(1L, MatchStatus.ACCEPTED, MatchingConstants.STATUS_TTL_SECONDS);
        verify(timeoutStore, never()).cleanup("match-1");
    }

    @Test
    @DisplayName("양쪽이 모두 수락하면 세션 상태를 ACCEPTED로 변경한다")
    void acceptByBoth() {
        MatchSession session = foundSession().accept(1L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.acceptWithLock("match-1", 2L);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        MatchSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.status()).isEqualTo(MatchStatus.ACCEPTED);
        assertThat(savedSession.isAcceptedByBoth()).isTrue();
        verify(userStatusStore).updateStatus(2L, MatchStatus.ACCEPTED, MatchingConstants.STATUS_TTL_SECONDS);
        verify(timeoutStore).cleanup("match-1");
        ArgumentCaptor<MatchResponseResultEvent> eventCaptor = ArgumentCaptor.forClass(MatchResponseResultEvent.class);
        verify(settlementEventPublisher).publish(eventCaptor.capture());
        MatchResponseResultEvent event = eventCaptor.getValue();
        assertThat(event.matchId()).isEqualTo("match-1");
        assertThat(event.sessionStatus()).isEqualTo(MatchStatus.ACCEPTED);
        assertThat(event.userAStatus()).isEqualTo(MatchResponseStatus.ACCEPTED);
        assertThat(event.userBStatus()).isEqualTo(MatchResponseStatus.ACCEPTED);
    }

    @Test
    @DisplayName("같은 유저의 중복 수락은 멱등하게 처리한다")
    void duplicatedAccept() {
        MatchSession session = foundSession().accept(1L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.acceptWithLock("match-1", 1L);

        verify(sessionStore, never()).save(session, MatchingConstants.MATCH_SESSION_TTL_SECONDS);
        verify(userStatusStore, never()).updateStatus(1L, MatchStatus.ACCEPTED, MatchingConstants.STATUS_TTL_SECONDS);
    }

    @Test
    @DisplayName("상대가 이미 수락했어도 거절 요청은 응답만 기록하고 deadline까지 세션을 유지한다")
    void rejectAfterAcceptedOpponentKeepsSessionOpen() {
        MatchSession session = foundSession().accept(1L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.rejectWithLock("match-1", 2L);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        MatchSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.status()).isEqualTo(MatchStatus.FOUND);
        assertThat(savedSession.userAStatus()).isEqualTo(MatchResponseStatus.ACCEPTED);
        assertThat(savedSession.userBStatus()).isEqualTo(MatchResponseStatus.REJECTED);
        verify(userStatusStore, never()).removeStatus(any());
        verify(matchStore, never()).add(any());
        verify(timeoutStore, never()).cleanup("match-1");
        verify(settlementEventPublisher, never()).publish(any(MatchResponseResultEvent.class));
    }

    @Test
    @DisplayName("거절 시 아직 응답하지 않은 상대는 10초 안에 응답할 수 있도록 세션을 유지한다")
    void rejectKeepsSessionOpenForPendingOpponent() {
        MatchSession session = foundSession();
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.rejectWithLock("match-1", 2L);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        MatchSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.status()).isEqualTo(MatchStatus.FOUND);
        assertThat(savedSession.userBStatus()).isEqualTo(MatchResponseStatus.REJECTED);
        verify(userStatusStore, never()).removeStatus(2L);
        verify(userStatusStore, never()).removeStatus(1L);
        verify(matchStore, never()).add(any());
        verify(timeoutStore, never()).cleanup("match-1");
        verify(settlementEventPublisher, never()).publish(any(MatchResponseResultEvent.class));
    }

    @Test
    @DisplayName("한 유저가 먼저 거절해도 상대의 제한 시간 내 수락은 응답만 기록하고 deadline까지 세션을 유지한다")
    void acceptAfterOpponentRejectKeepsSessionOpen() {
        MatchSession session = foundSession().reject(2L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.acceptWithLock("match-1", 1L);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        MatchSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.status()).isEqualTo(MatchStatus.FOUND);
        assertThat(savedSession.userAStatus()).isEqualTo(MatchResponseStatus.ACCEPTED);
        assertThat(savedSession.userBStatus()).isEqualTo(MatchResponseStatus.REJECTED);
        verify(userStatusStore).updateStatus(1L, MatchStatus.ACCEPTED, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore, never()).removeStatus(2L);
        verify(matchStore, never()).add(any());
        verify(timeoutStore, never()).cleanup("match-1");
    }

    @Test
    @DisplayName("거절 요청만으로는 최종 정산이 아니므로 timeout index를 정리하지 않는다")
    void rejectDoesNotCleanupTimeoutIndex() {
        MatchSession session = foundSession().accept(1L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.rejectWithLock("match-1", 2L);

        verify(timeoutStore, never()).cleanup("match-1");
    }

    @Test
    @DisplayName("timeout index cleanup 실패는 수락 API 성공을 깨지 않는다")
    void cleanupFailureDoesNotBreakAccept() {
        MatchSession session = foundSession().accept(1L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));
        doThrow(new IllegalStateException("cleanup failed")).when(timeoutStore).cleanup("match-1");

        processor.acceptWithLock("match-1", 2L);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        assertThat(sessionCaptor.getValue().status()).isEqualTo(MatchStatus.ACCEPTED);
        verify(timeoutStore).cleanup("match-1");
    }

    @Test
    @DisplayName("수락 시간이 만료된 세션은 EXPIRED 예외를 던진다")
    void expiredSession() {
        when(sessionStore.findById("match-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.acceptWithLock("match-1", 1L))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.MATCH_SESSION_EXPIRED);
    }

    @Test
    @DisplayName("참여자가 아닌 유저 요청은 NOT_PARTICIPANT 예외를 던진다")
    void notParticipant() {
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(foundSession()));

        assertThatThrownBy(() -> processor.rejectWithLock("match-1", 3L))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.MATCH_SESSION_NOT_PARTICIPANT);
    }

    @Test
    @DisplayName("이미 종료된 세션은 상태에 맞는 예외를 던진다")
    void alreadyFinished() {
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(foundSession().withStatus(MatchStatus.DECLINED)));

        assertThatThrownBy(() -> processor.acceptWithLock("match-1", 1L))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.MATCH_SESSION_ALREADY_DECLINED);
    }

    @Test
    @DisplayName("이미 수락한 유저는 거절할 수 없다")
    void acceptedUserCannotReject() {
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(foundSession().accept(1L)));

        assertThatThrownBy(() -> processor.rejectWithLock("match-1", 1L))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.MATCH_SESSION_ALREADY_ACCEPTED);
    }

    @Test
    @DisplayName("수락한 유저와 미응답 유저가 있으면 timeout 시 수락 유저는 큐에 복귀하고 미응답 유저는 TIMEOUT 처리한다")
    void timeoutReturnsAcceptedUserToQueue() {
        MatchSession session = foundSession().accept(1L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.timeoutWithLock("match-1");

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        MatchSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.status()).isEqualTo(MatchStatus.TIMEOUT);
        assertThat(savedSession.userAStatus()).isEqualTo(MatchResponseStatus.ACCEPTED);
        assertThat(savedSession.userBStatus()).isEqualTo(MatchResponseStatus.TIMEOUT);

        ArgumentCaptor<MatchTicket> ticketCaptor = ArgumentCaptor.forClass(MatchTicket.class);
        verify(matchStore).add(ticketCaptor.capture());
        MatchTicket ticket = ticketCaptor.getValue();
        assertThat(ticket.userId()).isEqualTo(1L);
        assertThat(ticket.tierScore()).isEqualTo(10);
        assertThat(ticket.entryTime()).isEqualTo(1000L);
        verify(userStatusStore).updateStatus(1L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore).removeStatus(2L);
        ArgumentCaptor<MatchResponseResultEvent> eventCaptor = ArgumentCaptor.forClass(MatchResponseResultEvent.class);
        verify(settlementEventPublisher).publish(eventCaptor.capture());
        MatchResponseResultEvent event = eventCaptor.getValue();
        assertThat(event.sessionStatus()).isEqualTo(MatchStatus.TIMEOUT);
        assertThat(event.userAStatus()).isEqualTo(MatchResponseStatus.ACCEPTED);
        assertThat(event.userBStatus()).isEqualTo(MatchResponseStatus.TIMEOUT);
    }

    @Test
    @DisplayName("거절한 유저와 미응답 유저가 있으면 timeout 시 두 유저 모두 큐에서 이탈한다")
    void timeoutAfterRejectRemovesBothUsers() {
        MatchSession session = foundSession().reject(1L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.timeoutWithLock("match-1");

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        MatchSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.status()).isEqualTo(MatchStatus.TIMEOUT);
        assertThat(savedSession.userAStatus()).isEqualTo(MatchResponseStatus.REJECTED);
        assertThat(savedSession.userBStatus()).isEqualTo(MatchResponseStatus.TIMEOUT);

        verify(userStatusStore).removeStatus(1L);
        verify(userStatusStore).removeStatus(2L);
        verify(matchStore, never()).add(any());
        verify(settlementEventPublisher).publish(any(MatchResponseResultEvent.class));
    }

    @Test
    @DisplayName("수락과 거절이 모두 기록된 세션은 deadline 시 DECLINED로 정산하고 수락 유저만 큐에 복귀시킨다")
    void deadlineDeclinesAcceptedAndRejectedSession() {
        MatchSession session = foundSession().accept(1L).reject(2L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.timeoutWithLock("match-1");

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        MatchSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.status()).isEqualTo(MatchStatus.DECLINED);
        assertThat(savedSession.userAStatus()).isEqualTo(MatchResponseStatus.ACCEPTED);
        assertThat(savedSession.userBStatus()).isEqualTo(MatchResponseStatus.REJECTED);

        ArgumentCaptor<MatchTicket> ticketCaptor = ArgumentCaptor.forClass(MatchTicket.class);
        verify(matchStore).add(ticketCaptor.capture());
        MatchTicket ticket = ticketCaptor.getValue();
        assertThat(ticket.userId()).isEqualTo(1L);
        assertThat(ticket.tierScore()).isEqualTo(10);
        assertThat(ticket.entryTime()).isEqualTo(1000L);
        verify(userStatusStore).updateStatus(1L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore).removeStatus(2L);
        verify(timeoutStore).cleanup("match-1");
        ArgumentCaptor<MatchResponseResultEvent> eventCaptor = ArgumentCaptor.forClass(MatchResponseResultEvent.class);
        verify(settlementEventPublisher).publish(eventCaptor.capture());
        MatchResponseResultEvent event = eventCaptor.getValue();
        assertThat(event.sessionStatus()).isEqualTo(MatchStatus.DECLINED);
        assertThat(event.userAStatus()).isEqualTo(MatchResponseStatus.ACCEPTED);
        assertThat(event.userBStatus()).isEqualTo(MatchResponseStatus.REJECTED);
    }

    @Test
    @DisplayName("양쪽 거절이 모두 기록된 세션은 deadline 시 DECLINED로 정산하고 두 유저 모두 이탈시킨다")
    void deadlineDeclinesBothRejectedSession() {
        MatchSession session = foundSession().reject(1L).reject(2L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.timeoutWithLock("match-1");

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        MatchSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.status()).isEqualTo(MatchStatus.DECLINED);
        assertThat(savedSession.userAStatus()).isEqualTo(MatchResponseStatus.REJECTED);
        assertThat(savedSession.userBStatus()).isEqualTo(MatchResponseStatus.REJECTED);

        verify(userStatusStore).removeStatus(1L);
        verify(userStatusStore).removeStatus(2L);
        verify(matchStore, never()).add(any());
        verify(timeoutStore).cleanup("match-1");
        verify(settlementEventPublisher).publish(any(MatchResponseResultEvent.class));
    }

    @Test
    @DisplayName("양쪽 모두 미응답이면 timeout 시 두 유저 모두 TIMEOUT 처리하고 큐에서 이탈한다")
    void timeoutBothPendingRemovesBothUsers() {
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(foundSession()));

        processor.timeoutWithLock("match-1");

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        MatchSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.status()).isEqualTo(MatchStatus.TIMEOUT);
        assertThat(savedSession.userAStatus()).isEqualTo(MatchResponseStatus.TIMEOUT);
        assertThat(savedSession.userBStatus()).isEqualTo(MatchResponseStatus.TIMEOUT);

        verify(userStatusStore).removeStatus(1L);
        verify(userStatusStore).removeStatus(2L);
        verify(matchStore, never()).add(any());
        verify(settlementEventPublisher).publish(any(MatchResponseResultEvent.class));
    }

    @Test
    @DisplayName("이미 종료된 세션 timeout 정산은 no-op 처리한다")
    void timeoutAlreadyFinishedSessionNoOp() {
        when(sessionStore.findById("match-1"))
                .thenReturn(Optional.of(foundSession().accept(1L).accept(2L).withStatus(MatchStatus.ACCEPTED)));

        processor.timeoutWithLock("match-1");

        verify(sessionStore, never()).save(any(MatchSession.class), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        verify(userStatusStore, never()).removeStatus(any());
        verify(matchStore, never()).add(any());
        verify(settlementEventPublisher, never()).publish(any(MatchResponseResultEvent.class));
    }

    @Test
    @DisplayName("이미 거절로 종료된 세션 timeout 정산은 no-op 처리한다")
    void timeoutAlreadyDeclinedSessionNoOp() {
        when(sessionStore.findById("match-1"))
                .thenReturn(Optional.of(foundSession().reject(1L).reject(2L).withStatus(MatchStatus.DECLINED)));

        processor.timeoutWithLock("match-1");

        verify(sessionStore, never()).save(any(MatchSession.class), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        verify(userStatusStore, never()).removeStatus(any());
        verify(matchStore, never()).add(any());
    }

    @Test
    @DisplayName("이미 timeout으로 종료된 세션 timeout 정산은 no-op 처리한다")
    void timeoutAlreadyTimeoutSessionNoOp() {
        when(sessionStore.findById("match-1"))
                .thenReturn(Optional.of(foundSession().timeoutPendingUsers().withStatus(MatchStatus.TIMEOUT)));

        processor.timeoutWithLock("match-1");

        verify(sessionStore, never()).save(any(MatchSession.class), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        verify(userStatusStore, never()).removeStatus(any());
        verify(matchStore, never()).add(any());
    }

    @Test
    @DisplayName("없는 세션 timeout 정산은 no-op 처리한다")
    void timeoutMissingSessionNoOp() {
        when(sessionStore.findById("match-1")).thenReturn(Optional.empty());

        processor.timeoutWithLock("match-1");

        verify(sessionStore, never()).save(any(MatchSession.class), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        verify(userStatusStore, never()).removeStatus(any());
        verify(matchStore, never()).add(any());
    }

    @Test
    @DisplayName("timeout이 먼저 세션을 종료하면 이후 accept 요청은 TIMEOUT 예외를 던진다")
    void acceptAfterTimeoutThrowsTimeout() {
        when(sessionStore.findById("match-1"))
                .thenReturn(Optional.of(foundSession().timeoutPendingUsers().withStatus(MatchStatus.TIMEOUT)));

        assertThatThrownBy(() -> processor.acceptWithLock("match-1", 1L))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.MATCH_SESSION_TIMEOUT);
    }

    @Test
    @DisplayName("timeout이 먼저 세션을 종료하면 이후 reject 요청은 TIMEOUT 예외를 던진다")
    void rejectAfterTimeoutThrowsTimeout() {
        when(sessionStore.findById("match-1"))
                .thenReturn(Optional.of(foundSession().timeoutPendingUsers().withStatus(MatchStatus.TIMEOUT)));

        assertThatThrownBy(() -> processor.rejectWithLock("match-1", 1L))
                .isInstanceOf(MatchingException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.MATCH_SESSION_TIMEOUT);
    }

    private MatchSession foundSession() {
        return new MatchSession(
                "match-1",
                1L,
                2L,
                10,
                11,
                1000L,
                2000L,
                MatchStatus.FOUND,
                3000L,
                MatchResponseStatus.PENDING,
                MatchResponseStatus.PENDING
        );
    }
}
