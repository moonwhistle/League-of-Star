package com.sang.smite.matching.service;

import com.sang.smite.domain.match.domain.MatchSession;
import com.sang.smite.domain.match.domain.MatchStatus;
import com.sang.smite.domain.match.domain.MatchResponseStatus;
import com.sang.smite.domain.match.domain.MatchTicket;
import com.sang.smite.matching.common.constant.MatchingConstants;
import com.sang.smite.matching.common.exception.MatchingErrorCode;
import com.sang.smite.matching.common.exception.MatchingException;
import com.sang.smite.matching.repository.MatchSessionStore;
import com.sang.smite.matching.repository.MatchStore;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchResponseProcessorTest {

    @InjectMocks
    private MatchResponseProcessor processor;

    @Mock
    private MatchSessionStore sessionStore;

    @Mock
    private MatchUserStatusStore userStatusStore;

    @Mock
    private MatchStore matchStore;

    @Test
    @DisplayName("수락 요청 시 해당 유저의 수락 상태와 유저 상태를 갱신한다")
    void accept() {
        MatchSession session = foundSession();
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.acceptWithLock("match-1", 1L);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(12L));
        MatchSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.userAStatus()).isEqualTo(MatchResponseStatus.ACCEPTED);
        assertThat(savedSession.userBStatus()).isEqualTo(MatchResponseStatus.PENDING);
        assertThat(savedSession.status()).isEqualTo(MatchStatus.FOUND);
        verify(userStatusStore).updateStatus(1L, MatchStatus.ACCEPTED, MatchingConstants.STATUS_TTL_SECONDS);
    }

    @Test
    @DisplayName("양쪽이 모두 수락하면 세션 상태를 ACCEPTED로 변경한다")
    void acceptByBoth() {
        MatchSession session = foundSession().accept(1L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.acceptWithLock("match-1", 2L);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(12L));
        MatchSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.status()).isEqualTo(MatchStatus.ACCEPTED);
        assertThat(savedSession.isAcceptedByBoth()).isTrue();
        verify(userStatusStore).updateStatus(2L, MatchStatus.ACCEPTED, MatchingConstants.STATUS_TTL_SECONDS);
    }

    @Test
    @DisplayName("같은 유저의 중복 수락은 멱등하게 처리한다")
    void duplicatedAccept() {
        MatchSession session = foundSession().accept(1L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.acceptWithLock("match-1", 1L);

        verify(sessionStore, never()).save(session, 12L);
        verify(userStatusStore, never()).updateStatus(1L, MatchStatus.ACCEPTED, MatchingConstants.STATUS_TTL_SECONDS);
    }

    @Test
    @DisplayName("거절 시 이미 수락한 상대는 기존 entryTime으로 큐에 복귀하고 세션을 종료한다")
    void rejectAndReturnAcceptedOpponentToQueue() {
        MatchSession session = foundSession().accept(1L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.rejectWithLock("match-1", 2L);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(12L));
        assertThat(sessionCaptor.getValue().status()).isEqualTo(MatchStatus.DECLINED);
        verify(userStatusStore).removeStatus(2L);

        ArgumentCaptor<MatchTicket> ticketCaptor = ArgumentCaptor.forClass(MatchTicket.class);
        verify(matchStore).add(ticketCaptor.capture());
        MatchTicket ticket = ticketCaptor.getValue();
        assertThat(ticket.userId()).isEqualTo(1L);
        assertThat(ticket.tierScore()).isEqualTo(10);
        assertThat(ticket.entryTime()).isEqualTo(1000L);
        verify(userStatusStore).updateStatus(1L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
    }

    @Test
    @DisplayName("거절 시 아직 응답하지 않은 상대는 10초 안에 응답할 수 있도록 세션을 유지한다")
    void rejectKeepsSessionOpenForPendingOpponent() {
        MatchSession session = foundSession();
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.rejectWithLock("match-1", 2L);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(12L));
        MatchSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.status()).isEqualTo(MatchStatus.FOUND);
        assertThat(savedSession.userBStatus()).isEqualTo(MatchResponseStatus.REJECTED);
        verify(userStatusStore).removeStatus(2L);
        verify(userStatusStore, never()).removeStatus(1L);
        verify(matchStore, never()).add(any());
    }

    @Test
    @DisplayName("한 유저가 먼저 거절해도 상대가 제한 시간 안에 수락하면 수락한 상대를 큐에 복귀시킨다")
    void acceptAfterOpponentRejectReturnsAcceptedUserToQueue() {
        MatchSession session = foundSession().reject(2L);
        when(sessionStore.findById("match-1")).thenReturn(Optional.of(session));

        processor.acceptWithLock("match-1", 1L);

        ArgumentCaptor<MatchSession> sessionCaptor = ArgumentCaptor.forClass(MatchSession.class);
        verify(sessionStore).save(sessionCaptor.capture(), eq(12L));
        assertThat(sessionCaptor.getValue().status()).isEqualTo(MatchStatus.DECLINED);

        ArgumentCaptor<MatchTicket> ticketCaptor = ArgumentCaptor.forClass(MatchTicket.class);
        verify(matchStore).add(ticketCaptor.capture());
        MatchTicket ticket = ticketCaptor.getValue();
        assertThat(ticket.userId()).isEqualTo(1L);
        assertThat(ticket.tierScore()).isEqualTo(10);
        assertThat(ticket.entryTime()).isEqualTo(1000L);
        verify(userStatusStore).updateStatus(1L, MatchStatus.MATCHING, MatchingConstants.STATUS_TTL_SECONDS);
        verify(userStatusStore).removeStatus(2L);
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
