package com.sang.leagueofstar.notification.match.factory;

import com.sang.leagueofstar.domain.match.domain.MatchResponseStatus;
import com.sang.leagueofstar.domain.match.domain.MatchStatus;
import com.sang.leagueofstar.matching.domain.event.MatchResponseResultEvent;
import com.sang.leagueofstar.notification.match.dto.MatchResponseAction;
import com.sang.leagueofstar.notification.match.dto.MatchResponseOutcome;
import com.sang.leagueofstar.notification.match.dto.MatchResponseReason;
import com.sang.leagueofstar.notification.match.dto.MatchResponseResultNotification;
import com.sang.leagueofstar.notification.match.provider.MatchOpponentProfileProvider;
import com.sang.leagueofstar.notification.match.pubsub.dto.MatchResponseResultPubSubMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * match_response_result 이벤트를 대상 유저 관점의 SSE Pub/Sub 메시지로 생성합니다.
 */
@Component
@RequiredArgsConstructor
public class MatchResponseResultNotificationFactory {

    private final MatchOpponentProfileProvider opponentProfileProvider;

    public MatchResponseResultPubSubMessage createForUserA(MatchResponseResultEvent event) {
        return create(
                event.userA(),
                event.userB(),
                event.userAStatus(),
                event.userBStatus(),
                event.userBTierScore(),
                event
        );
    }

    public MatchResponseResultPubSubMessage createForUserB(MatchResponseResultEvent event) {
        return create(
                event.userB(),
                event.userA(),
                event.userBStatus(),
                event.userAStatus(),
                event.userATierScore(),
                event
        );
    }

    private MatchResponseResultPubSubMessage create(
            Long targetUserId,
            Long opponentUserId,
            MatchResponseStatus myStatus,
            MatchResponseStatus opponentStatus,
            int opponentTierScore,
            MatchResponseResultEvent event
    ) {
        MatchResponseResultNotification notification = new MatchResponseResultNotification(
                event.matchId(),
                outcomeOf(event.sessionStatus()),
                reasonOf(myStatus, opponentStatus, event.sessionStatus()),
                actionOf(myStatus, opponentStatus, event.sessionStatus()),
                opponentProfileProvider.getOpponent(opponentUserId, opponentTierScore),
                gameOf(event)
        );
        return new MatchResponseResultPubSubMessage(targetUserId, notification);
    }

    private MatchResponseOutcome outcomeOf(MatchStatus sessionStatus) {
        if (sessionStatus == MatchStatus.ACCEPTED) {
            return MatchResponseOutcome.MATCHED;
        }
        return MatchResponseOutcome.FAILED;
    }

    private MatchResponseReason reasonOf(
            MatchResponseStatus myStatus,
            MatchResponseStatus opponentStatus,
            MatchStatus sessionStatus
    ) {
        if (sessionStatus == MatchStatus.GAME_SETUP_FAILED) {
            return MatchResponseReason.GAME_SETUP_FAILED;
        }
        if (myStatus == MatchResponseStatus.ACCEPTED && opponentStatus == MatchResponseStatus.ACCEPTED) {
            return MatchResponseReason.BOTH_ACCEPTED;
        }
        if (myStatus == MatchResponseStatus.REJECTED) {
            return MatchResponseReason.MY_REJECTED;
        }
        if (myStatus == MatchResponseStatus.TIMEOUT && opponentStatus == MatchResponseStatus.TIMEOUT) {
            return MatchResponseReason.BOTH_TIMEOUT;
        }
        if (myStatus == MatchResponseStatus.TIMEOUT) {
            return MatchResponseReason.MY_TIMEOUT;
        }
        if (opponentStatus == MatchResponseStatus.REJECTED) {
            return MatchResponseReason.OPPONENT_REJECTED;
        }
        if (opponentStatus == MatchResponseStatus.TIMEOUT) {
            return MatchResponseReason.OPPONENT_TIMEOUT;
        }
        throw new IllegalArgumentException("지원하지 않는 매칭 응답 정산 상태입니다.");
    }

    private MatchResponseAction actionOf(
            MatchResponseStatus myStatus,
            MatchResponseStatus opponentStatus,
            MatchStatus sessionStatus
    ) {
        if (sessionStatus == MatchStatus.ACCEPTED) {
            return MatchResponseAction.GO_TO_GAME_WAITING;
        }
        if (sessionStatus == MatchStatus.GAME_SETUP_FAILED) {
            return MatchResponseAction.GO_TO_MATCH_START;
        }
        if (myStatus == MatchResponseStatus.ACCEPTED
                && (opponentStatus == MatchResponseStatus.REJECTED || opponentStatus == MatchResponseStatus.TIMEOUT)) {
            return MatchResponseAction.RETURN_TO_MATCHING;
        }
        return MatchResponseAction.GO_TO_MATCH_START;
    }

    private MatchResponseResultNotification.Game gameOf(MatchResponseResultEvent event) {
        MatchResponseResultEvent.Game game = event.game();
        if (game == null) {
            return null;
        }
        return new MatchResponseResultNotification.Game(
                game.gameRoomId(),
                game.webSocketUrl()
        );
    }
}
