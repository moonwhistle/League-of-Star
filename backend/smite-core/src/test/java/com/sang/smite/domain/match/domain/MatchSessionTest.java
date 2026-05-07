package com.sang.smite.domain.match.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchSessionTest {

    @Test
    @DisplayName("매칭 세션 생성 시 수락 대기 상태와 미수락 상태로 초기화한다.")
    void create() {
        MatchSession session = MatchSession.create("match-1", 1L, 2L);

        assertThat(session.matchId()).isEqualTo("match-1");
        assertThat(session.userA()).isEqualTo(1L);
        assertThat(session.userB()).isEqualTo(2L);
        assertThat(session.status()).isEqualTo(MatchStatus.FOUND);
        assertThat(session.userAAccepted()).isFalse();
        assertThat(session.userBAccepted()).isFalse();
    }

    @Test
    @DisplayName("세션 참여자 여부를 확인한다.")
    void isParticipant() {
        MatchSession session = new MatchSession("match-1", 1L, 2L, MatchStatus.FOUND, 1000L, false, false);

        assertThat(session.isParticipant(1L)).isTrue();
        assertThat(session.isParticipant(2L)).isTrue();
        assertThat(session.isParticipant(3L)).isFalse();
        assertThat(session.isUserA(1L)).isTrue();
        assertThat(session.isUserB(2L)).isTrue();
    }

    @Test
    @DisplayName("두 유저가 모두 수락했는지 확인한다.")
    void isAcceptedByBoth() {
        MatchSession notAccepted = new MatchSession("match-1", 1L, 2L, MatchStatus.FOUND, 1000L, true, false);
        MatchSession acceptedByBoth = new MatchSession("match-1", 1L, 2L, MatchStatus.ACCEPTED, 1000L, true, true);

        assertThat(notAccepted.isAcceptedByBoth()).isFalse();
        assertThat(acceptedByBoth.isAcceptedByBoth()).isTrue();
    }
}
