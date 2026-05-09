package com.sang.smite.domain.match.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchSessionTest {

    @Test
    @DisplayName("매칭 세션 생성 시 수락 대기 상태와 미수락 상태로 초기화한다.")
    void create() {
        MatchSession session = MatchSession.create("match-1", 1L, 2L, 10, 11, 1000L, 2000L);

        assertThat(session.matchId()).isEqualTo("match-1");
        assertThat(session.userA()).isEqualTo(1L);
        assertThat(session.userB()).isEqualTo(2L);
        assertThat(session.userATierScore()).isEqualTo(10);
        assertThat(session.userBTierScore()).isEqualTo(11);
        assertThat(session.userAEntryTime()).isEqualTo(1000L);
        assertThat(session.userBEntryTime()).isEqualTo(2000L);
        assertThat(session.status()).isEqualTo(MatchStatus.FOUND);
        assertThat(session.userAAccepted()).isFalse();
        assertThat(session.userBAccepted()).isFalse();
    }

    @Test
    @DisplayName("세션 참여자 여부를 확인한다.")
    void isParticipant() {
        MatchSession session = new MatchSession("match-1", 1L, 2L, 10, 11, 1000L, 2000L,
                MatchStatus.FOUND, 3000L, false, false);

        assertThat(session.isParticipant(1L)).isTrue();
        assertThat(session.isParticipant(2L)).isTrue();
        assertThat(session.isParticipant(3L)).isFalse();
        assertThat(session.isUserA(1L)).isTrue();
        assertThat(session.isUserB(2L)).isTrue();
    }

    @Test
    @DisplayName("두 유저가 모두 수락했는지 확인한다.")
    void isAcceptedByBoth() {
        MatchSession notAccepted = new MatchSession("match-1", 1L, 2L, 10, 11, 1000L, 2000L,
                MatchStatus.FOUND, 3000L, true, false);
        MatchSession acceptedByBoth = new MatchSession("match-1", 1L, 2L, 10, 11, 1000L, 2000L,
                MatchStatus.ACCEPTED, 3000L, true, true);

        assertThat(notAccepted.isAcceptedByBoth()).isFalse();
        assertThat(acceptedByBoth.isAcceptedByBoth()).isTrue();
    }

    @Test
    @DisplayName("참여자별 tierScore와 entryTime을 조회한다.")
    void queueSnapshotOfParticipant() {
        MatchSession session = MatchSession.create("match-1", 1L, 2L, 10, 11, 1000L, 2000L);

        assertThat(session.tierScoreOf(1L)).isEqualTo(10);
        assertThat(session.tierScoreOf(2L)).isEqualTo(11);
        assertThat(session.entryTimeOf(1L)).isEqualTo(1000L);
        assertThat(session.entryTimeOf(2L)).isEqualTo(2000L);
    }

    @Test
    @DisplayName("특정 참여자의 수락 상태를 변경한다.")
    void accept() {
        MatchSession session = MatchSession.create("match-1", 1L, 2L, 10, 11, 1000L, 2000L);

        MatchSession acceptedByA = session.accept(1L);
        MatchSession acceptedByBoth = acceptedByA.accept(2L);

        assertThat(acceptedByA.userAAccepted()).isTrue();
        assertThat(acceptedByA.userBAccepted()).isFalse();
        assertThat(acceptedByA.acceptedBy(1L)).isTrue();
        assertThat(acceptedByBoth.isAcceptedByBoth()).isTrue();
    }

    @Test
    @DisplayName("세션 상태만 변경한 새 세션을 생성한다.")
    void withStatus() {
        MatchSession session = MatchSession.create("match-1", 1L, 2L, 10, 11, 1000L, 2000L)
                .accept(1L)
                .accept(2L);

        MatchSession accepted = session.withStatus(MatchStatus.ACCEPTED);

        assertThat(accepted.status()).isEqualTo(MatchStatus.ACCEPTED);
        assertThat(accepted.userAAccepted()).isTrue();
        assertThat(accepted.userBAccepted()).isTrue();
        assertThat(accepted.userATierScore()).isEqualTo(10);
        assertThat(accepted.userBEntryTime()).isEqualTo(2000L);
    }
}
