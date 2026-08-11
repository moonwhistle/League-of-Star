package com.sang.leagueofstar.notification.match.factory;

import com.sang.leagueofstar.domain.match.domain.MatchResponseStatus;
import com.sang.leagueofstar.domain.match.domain.MatchStatus;
import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Rank;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import com.sang.leagueofstar.domain.rank.service.RankReadService;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import com.sang.leagueofstar.matching.domain.event.MatchResponseResultEvent;
import com.sang.leagueofstar.notification.match.dto.MatchResponseAction;
import com.sang.leagueofstar.notification.match.dto.MatchResponseOutcome;
import com.sang.leagueofstar.notification.match.dto.MatchResponseReason;
import com.sang.leagueofstar.notification.match.provider.MatchOpponentProfileProvider;
import com.sang.leagueofstar.notification.match.pubsub.dto.MatchResponseResultPubSubMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchResponseResultNotificationFactoryTest {

    private final UserReadService userReadService = mock(UserReadService.class);
    private final RankReadService rankReadService = mock(RankReadService.class);
    private final MatchOpponentProfileProvider opponentProfileProvider =
            new MatchOpponentProfileProvider(userReadService, rankReadService);
    private final MatchResponseResultNotificationFactory factory =
            new MatchResponseResultNotificationFactory(opponentProfileProvider);

    @Test
    @DisplayName("거절한 유저와 수락한 상대에게 유저 관점별 match_response_result 메시지를 생성한다")
    void createRejectAndAcceptResult() {
        MatchResponseResultEvent event = new MatchResponseResultEvent(
                  "match-1",
                  1L,
                  2L,
                  MatchStatus.DECLINED,
                MatchResponseStatus.REJECTED,
                MatchResponseStatus.ACCEPTED
        );
        givenUser(1L, "rejecter", Tier.SILVER, Division.I);
        givenUser(2L, "accepter", Tier.GOLD, Division.IV);

        MatchResponseResultPubSubMessage userAMessage = factory.createForUserA(event);
        MatchResponseResultPubSubMessage userBMessage = factory.createForUserB(event);

        assertThat(userAMessage.targetUserId()).isEqualTo(1L);
        assertThat(userAMessage.notification().outcome()).isEqualTo(MatchResponseOutcome.FAILED);
        assertThat(userAMessage.notification().reason()).isEqualTo(MatchResponseReason.MY_REJECTED);
        assertThat(userAMessage.notification().action()).isEqualTo(MatchResponseAction.GO_TO_MATCH_START);
        assertThat(userAMessage.notification().opponent().nickname()).isEqualTo("accepter");
        assertThat(userAMessage.notification().opponent().tier()).isEqualTo("GOLD_IV");

        assertThat(userBMessage.targetUserId()).isEqualTo(2L);
        assertThat(userBMessage.notification().outcome()).isEqualTo(MatchResponseOutcome.FAILED);
        assertThat(userBMessage.notification().reason()).isEqualTo(MatchResponseReason.OPPONENT_REJECTED);
        assertThat(userBMessage.notification().action()).isEqualTo(MatchResponseAction.RETURN_TO_MATCHING);
        assertThat(userBMessage.notification().opponent().nickname()).isEqualTo("rejecter");
        assertThat(userBMessage.notification().opponent().tier()).isEqualTo("SILVER_I");
    }

    @Test
    @DisplayName("양쪽 수락 최종 결과는 게임 대기 화면 이동 메시지로 생성한다")
    void createBothAcceptedResult() {
        MatchResponseResultEvent event = new MatchResponseResultEvent(
                  "match-1",
                  1L,
                  2L,
                  MatchStatus.ACCEPTED,
                MatchResponseStatus.ACCEPTED,
                MatchResponseStatus.ACCEPTED,
                new MatchResponseResultEvent.Game(
                        100L,
                        "/ws/game/100"
                )
        );
        givenUser(1L, "userA", Tier.SILVER, Division.I);
        givenUser(2L, "userB", Tier.GOLD, Division.IV);

        MatchResponseResultPubSubMessage userAMessage = factory.createForUserA(event);
        MatchResponseResultPubSubMessage userBMessage = factory.createForUserB(event);

        assertThat(userAMessage.notification().outcome()).isEqualTo(MatchResponseOutcome.MATCHED);
        assertThat(userAMessage.notification().reason()).isEqualTo(MatchResponseReason.BOTH_ACCEPTED);
        assertThat(userAMessage.notification().action()).isEqualTo(MatchResponseAction.GO_TO_GAME_WAITING);
        assertThat(userAMessage.notification().game().gameRoomId()).isEqualTo(100L);
        assertThat(userAMessage.notification().game().webSocketUrl()).isEqualTo("/ws/game/100");

        assertThat(userBMessage.notification().outcome()).isEqualTo(MatchResponseOutcome.MATCHED);
        assertThat(userBMessage.notification().reason()).isEqualTo(MatchResponseReason.BOTH_ACCEPTED);
        assertThat(userBMessage.notification().action()).isEqualTo(MatchResponseAction.GO_TO_GAME_WAITING);
        assertThat(userBMessage.notification().game().gameRoomId()).isEqualTo(100L);
        assertThat(userBMessage.notification().game().webSocketUrl()).isEqualTo("/ws/game/100");
    }

    @Test
    @DisplayName("상대가 배치 진행 중이면 Unranked로 표시한다")
    void createResultWithPlacementOpponentProfile() {
        MatchResponseResultEvent event = new MatchResponseResultEvent(
                  "match-1",
                  1L,
                  2L,
                  MatchStatus.ACCEPTED,
                MatchResponseStatus.ACCEPTED,
                MatchResponseStatus.ACCEPTED
        );
        givenUser(1L, "userA", Tier.SILVER, Division.I);
        givenUser(2L, "placement", Tier.IRON, Division.IV);
        when(rankReadService.isPlacementInProgress(2L)).thenReturn(true);

        MatchResponseResultPubSubMessage userAMessage = factory.createForUserA(event);

        assertThat(userAMessage.notification().opponent().nickname()).isEqualTo("placement");
        assertThat(userAMessage.notification().opponent().tier()).isEqualTo("Unranked");
        assertThat(userAMessage.notification().opponent().tierScore()).isZero();
    }

    @Test
    @DisplayName("수락 유저와 timeout 유저에게 유저 관점별 실패 메시지를 생성한다")
    void createAcceptAndTimeoutResult() {
        MatchResponseResultEvent event = new MatchResponseResultEvent(
                  "match-1",
                  1L,
                  2L,
                  MatchStatus.TIMEOUT,
                MatchResponseStatus.ACCEPTED,
                MatchResponseStatus.TIMEOUT
        );
        givenUser(1L, "accepter", Tier.SILVER, Division.I);
        givenUser(2L, "timeout", Tier.GOLD, Division.IV);

        MatchResponseResultPubSubMessage userAMessage = factory.createForUserA(event);
        MatchResponseResultPubSubMessage userBMessage = factory.createForUserB(event);

        assertThat(userAMessage.notification().outcome()).isEqualTo(MatchResponseOutcome.FAILED);
        assertThat(userAMessage.notification().reason()).isEqualTo(MatchResponseReason.OPPONENT_TIMEOUT);
        assertThat(userAMessage.notification().action()).isEqualTo(MatchResponseAction.RETURN_TO_MATCHING);

        assertThat(userBMessage.notification().outcome()).isEqualTo(MatchResponseOutcome.FAILED);
        assertThat(userBMessage.notification().reason()).isEqualTo(MatchResponseReason.MY_TIMEOUT);
        assertThat(userBMessage.notification().action()).isEqualTo(MatchResponseAction.GO_TO_MATCH_START);
    }

    @Test
    @DisplayName("거절 유저와 timeout 유저에게 유저 관점별 실패 메시지를 생성한다")
    void createRejectAndTimeoutResult() {
        MatchResponseResultEvent event = new MatchResponseResultEvent(
                  "match-1",
                  1L,
                  2L,
                  MatchStatus.TIMEOUT,
                MatchResponseStatus.REJECTED,
                MatchResponseStatus.TIMEOUT
        );
        givenUser(1L, "rejecter", Tier.SILVER, Division.I);
        givenUser(2L, "timeout", Tier.GOLD, Division.IV);

        MatchResponseResultPubSubMessage userAMessage = factory.createForUserA(event);
        MatchResponseResultPubSubMessage userBMessage = factory.createForUserB(event);

        assertThat(userAMessage.notification().outcome()).isEqualTo(MatchResponseOutcome.FAILED);
        assertThat(userAMessage.notification().reason()).isEqualTo(MatchResponseReason.MY_REJECTED);
        assertThat(userAMessage.notification().action()).isEqualTo(MatchResponseAction.GO_TO_MATCH_START);

        assertThat(userBMessage.notification().outcome()).isEqualTo(MatchResponseOutcome.FAILED);
        assertThat(userBMessage.notification().reason()).isEqualTo(MatchResponseReason.MY_TIMEOUT);
        assertThat(userBMessage.notification().action()).isEqualTo(MatchResponseAction.GO_TO_MATCH_START);
    }

    @Test
    @DisplayName("양쪽 timeout 최종 결과는 양쪽 모두 start 화면 복귀 메시지로 생성한다")
    void createBothTimeoutResult() {
        MatchResponseResultEvent event = new MatchResponseResultEvent(
                  "match-1",
                  1L,
                  2L,
                  MatchStatus.TIMEOUT,
                MatchResponseStatus.TIMEOUT,
                MatchResponseStatus.TIMEOUT
        );
        givenUser(1L, "userA", Tier.SILVER, Division.I);
        givenUser(2L, "userB", Tier.GOLD, Division.IV);

        MatchResponseResultPubSubMessage userAMessage = factory.createForUserA(event);
        MatchResponseResultPubSubMessage userBMessage = factory.createForUserB(event);

        assertThat(userAMessage.notification().outcome()).isEqualTo(MatchResponseOutcome.FAILED);
        assertThat(userAMessage.notification().reason()).isEqualTo(MatchResponseReason.BOTH_TIMEOUT);
        assertThat(userAMessage.notification().action()).isEqualTo(MatchResponseAction.GO_TO_MATCH_START);

        assertThat(userBMessage.notification().outcome()).isEqualTo(MatchResponseOutcome.FAILED);
        assertThat(userBMessage.notification().reason()).isEqualTo(MatchResponseReason.BOTH_TIMEOUT);
        assertThat(userBMessage.notification().action()).isEqualTo(MatchResponseAction.GO_TO_MATCH_START);
    }

    @Test
    @DisplayName("게임 준비 실패 결과는 양쪽 모두 start 화면 복귀 메시지로 생성한다")
    void createGameSetupFailedResult() {
        MatchResponseResultEvent event = new MatchResponseResultEvent(
                  "match-1",
                  1L,
                  2L,
                  MatchStatus.GAME_SETUP_FAILED,
                MatchResponseStatus.ACCEPTED,
                MatchResponseStatus.ACCEPTED
        );
        givenUser(1L, "userA", Tier.SILVER, Division.I);
        givenUser(2L, "userB", Tier.GOLD, Division.IV);

        MatchResponseResultPubSubMessage userAMessage = factory.createForUserA(event);
        MatchResponseResultPubSubMessage userBMessage = factory.createForUserB(event);

        assertThat(userAMessage.notification().outcome()).isEqualTo(MatchResponseOutcome.FAILED);
        assertThat(userAMessage.notification().reason()).isEqualTo(MatchResponseReason.GAME_SETUP_FAILED);
        assertThat(userAMessage.notification().action()).isEqualTo(MatchResponseAction.GO_TO_MATCH_START);
        assertThat(userAMessage.notification().game()).isNull();

        assertThat(userBMessage.notification().outcome()).isEqualTo(MatchResponseOutcome.FAILED);
        assertThat(userBMessage.notification().reason()).isEqualTo(MatchResponseReason.GAME_SETUP_FAILED);
        assertThat(userBMessage.notification().action()).isEqualTo(MatchResponseAction.GO_TO_MATCH_START);
        assertThat(userBMessage.notification().game()).isNull();
    }

    private void givenUser(Long userId, String nickname, Tier tier, Division division) {
        User user = User.builder()
                .id(userId)
                .email(nickname + "@test.com")
                .nickname(nickname)
                .build();
        UserRankInfo rankInfo = UserRankInfo.builder()
                .userId(userId)
                .rank(Rank.of(tier, division))
                .build();

        when(userReadService.findById(userId)).thenReturn(user);
        when(rankReadService.getUserRankInfo(userId)).thenReturn(rankInfo);
    }
}
