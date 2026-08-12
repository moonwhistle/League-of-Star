package com.sang.leagueofstar.matching.domain.service;

import com.sang.leagueofstar.domain.match.domain.MatchClaim;
import com.sang.leagueofstar.domain.match.domain.MatchSession;
import com.sang.leagueofstar.domain.match.domain.MatchTicket;
import com.sang.leagueofstar.domain.match.event.MatchFoundEvent;
import com.sang.leagueofstar.matching.common.constant.MatchingConstants;
import com.sang.leagueofstar.matching.repository.MatchJobStore;
import com.sang.leagueofstar.matching.repository.MatchSessionStore;
import com.sang.leagueofstar.matching.repository.MatchTimeoutStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class MatchFoundServiceTest {

    @InjectMocks
    private MatchFoundService matchFoundService;

    @Mock
    private MatchJobStore matchJobStore;
    @Mock
    private MatchSessionStore sessionStore;
    @Mock
    private MatchTimeoutStore timeoutStore;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private Clock clock;

    @Test
    @DisplayName("batch 세션과 timeout을 준비한 뒤 한 번의 ACK로 매칭을 확정한다")
    void processBatch() {
        // given
        List<MatchClaim> claims = List.of(claim("batch:1", 1L), claim("batch:2", 3L));
        given(clock.millis()).willReturn(1_000L);
        given(matchJobStore.complete(claims, MatchingConstants.STATUS_TTL_SECONDS)).willReturn(2);

        // when
        List<MatchClaim> completed = matchFoundService.processBatch(claims);

        // then
        assertThat(completed).containsExactlyElementsOf(claims);
        InOrder inOrder = inOrder(sessionStore, timeoutStore, matchJobStore, eventPublisher);
        inOrder.verify(sessionStore).save(any(MatchSession.class), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        inOrder.verify(timeoutStore).addPending(claims.get(0).claimId(), 11_000L);
        inOrder.verify(sessionStore).save(any(MatchSession.class), eq(MatchingConstants.MATCH_SESSION_TTL_SECONDS));
        inOrder.verify(timeoutStore).addPending(claims.get(1).claimId(), 11_000L);
        inOrder.verify(matchJobStore).complete(claims, MatchingConstants.STATUS_TTL_SECONDS);
        inOrder.verify(eventPublisher, times(2)).publishEvent(any(MatchFoundEvent.class));
    }

    @Test
    @DisplayName("세션 저장에 실패한 작업은 PEL에 유지하고 나머지는 완료한다")
    void continuesBatchWhenSessionSaveFails() {
        // given
        MatchClaim failed = claim("batch:1", 1L);
        MatchClaim succeeded = claim("batch:2", 3L);
        willThrow(new RuntimeException("session failure"))
                .willDoNothing()
                .given(sessionStore).save(any(), anyLong());
        given(clock.millis()).willReturn(1_000L);
        given(matchJobStore.complete(List.of(succeeded), MatchingConstants.STATUS_TTL_SECONDS))
                .willReturn(1);

        // when
        List<MatchClaim> completed = matchFoundService.processBatch(List.of(failed, succeeded));

        // then
        assertThat(completed).containsExactly(succeeded);
        then(matchJobStore).should().complete(List.of(succeeded), MatchingConstants.STATUS_TTL_SECONDS);
    }

    @Test
    @DisplayName("timeout 등록 실패 시 세션을 삭제하고 해당 작업을 PEL에 유지한다")
    void keepsJobPendingWhenTimeoutRegistrationFails() {
        // given
        MatchClaim claim = claim("batch:1", 1L);
        willThrow(new RuntimeException("timeout failure")).given(timeoutStore).addPending(any(), anyLong());

        // when
        List<MatchClaim> completed = matchFoundService.processBatch(List.of(claim));

        // then
        assertThat(completed).isEmpty();
        then(sessionStore).should().delete(claim.claimId());
        then(matchJobStore).should(never()).complete(anyList(), anyLong());
    }

    @Test
    @DisplayName("batch ACK가 불일치하면 동시 완료 가능성을 고려해 준비 상태를 유지한다")
    void keepsPreparedStateWhenBatchCannotComplete() {
        // given
        List<MatchClaim> claims = List.of(claim("batch:1", 1L), claim("batch:2", 3L));
        given(clock.millis()).willReturn(1_000L);
        given(matchJobStore.complete(claims, MatchingConstants.STATUS_TTL_SECONDS)).willReturn(0);

        // when & then
        assertThatThrownBy(() -> matchFoundService.processBatch(claims)).isInstanceOf(RuntimeException.class);
        then(timeoutStore).should(never()).cleanup(any());
        then(sessionStore).should(never()).delete(any());
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("batch ACK 결과가 불확실하면 PEL recovery를 위해 준비 상태를 유지한다")
    void keepsPreparedStateWhenCompleteClaimsThrows() {
        // given
        List<MatchClaim> claims = List.of(claim("batch:1", 1L));
        RuntimeException cause = new RuntimeException("redis response lost");
        given(clock.millis()).willReturn(1_000L);
        willThrow(cause).given(matchJobStore)
                .complete(claims, MatchingConstants.STATUS_TTL_SECONDS);

        // when & then
        assertThatThrownBy(() -> matchFoundService.processBatch(claims)).isSameAs(cause);
        then(timeoutStore).should(never()).cleanup(any());
        then(sessionStore).should(never()).delete(any());
    }

    @Test
    @DisplayName("이벤트 발행 실패는 확정된 batch를 롤백하지 않는다")
    void isolatesEventPublicationFailure() {
        // given
        List<MatchClaim> claims = List.of(claim("batch:1", 1L));
        given(clock.millis()).willReturn(1_000L);
        given(matchJobStore.complete(claims, MatchingConstants.STATUS_TTL_SECONDS)).willReturn(1);
        willThrow(new RuntimeException("event failure")).given(eventPublisher).publishEvent(any());

        // when & then
        assertThatCode(() -> matchFoundService.processBatch(claims)).doesNotThrowAnyException();
        then(matchJobStore).should().complete(claims, MatchingConstants.STATUS_TTL_SECONDS);
    }

    @Test
    @DisplayName("이벤트에는 claim ID와 두 사용자 정보가 포함된다")
    void publishesClaimInformation() {
        // given
        MatchClaim claim = claim("batch:1", 1L);
        given(clock.millis()).willReturn(1_000L);
        given(matchJobStore.complete(List.of(claim), MatchingConstants.STATUS_TTL_SECONDS))
                .willReturn(1);
        ArgumentCaptor<MatchFoundEvent> eventCaptor = ArgumentCaptor.forClass(MatchFoundEvent.class);

        // when
        matchFoundService.processBatch(List.of(claim));

        // then
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().matchId()).isEqualTo(claim.claimId());
        assertThat(eventCaptor.getValue().userA()).isEqualTo(claim.first().userId());
        assertThat(eventCaptor.getValue().userB()).isEqualTo(claim.second().userId());
    }

    private MatchClaim claim(String claimId, long firstUserId) {
        return new MatchClaim(
                claimId,
                new MatchTicket(firstUserId, firstUserId * 1_000L),
                new MatchTicket(firstUserId + 1, (firstUserId + 1) * 1_000L)
        );
    }
}
