package com.sang.leagueofstar.game.result.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.GameRules;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.game.result.domain.GameResultReason;
import com.sang.leagueofstar.game.result.dto.GameResultPayload;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GameResultPayloadFactory {

    public GameResultPayload lightningKill(Long gameRoomId,
                                       GameResult result,
                                       Long winnerUserId,
                                       long finishedAt,
                                       List<GameAction> actions) {
        return create(
                gameRoomId,
                result,
                winnerUserId,
                GameResultReason.LIGHTNING_KILL,
                finishedAt,
                actions
        );
    }

    public GameResultPayload bothLightningsUsedDraw(Long gameRoomId,
                                                GameResult result,
                                                Long winnerUserId,
                                                long finishedAt,
                                                List<GameAction> actions) {
        return create(
                gameRoomId,
                result,
                winnerUserId,
                GameResultReason.BOTH_LIGHTNINGS_USED_DRAW,
                finishedAt,
                actions
        );
    }

    public GameResultPayload naturalDeathDraw(Long gameRoomId,
                                              GameResult result,
                                              Long winnerUserId,
                                              long finishedAt,
                                              List<GameAction> actions) {
        return create(
                gameRoomId,
                result,
                winnerUserId,
                GameResultReason.NATURAL_DEATH_DRAW,
                finishedAt,
                actions
        );
    }

    public GameResultPayload currentResult(Long gameRoomId,
                                           GameResult result,
                                           Long winnerUserId,
                                           long finishedAt,
                                           List<GameAction> actions) {
        return create(
                gameRoomId,
                result,
                winnerUserId,
                resolveCurrentResultReason(winnerUserId, actions),
                finishedAt,
                actions
        );
    }

    private GameResultPayload create(Long gameRoomId,
                                     GameResult result,
                                     Long winnerUserId,
                                     GameResultReason reason,
                                     long finishedAt,
                                     List<GameAction> actions) {
        return new GameResultPayload(
                gameRoomId,
                result,
                winnerUserId,
                reason.getCode(),
                finishedAt,
                actions.stream()
                        .map(this::toActionSummary)
                        .toList()
        );
    }

    private GameResultReason resolveCurrentResultReason(Long winnerUserId, List<GameAction> actions) {
        if (winnerUserId != null) {
            return GameResultReason.LIGHTNING_KILL;
        }
        if (bothUsersUsedLightningWithoutKill(actions)) {
            return GameResultReason.BOTH_LIGHTNINGS_USED_DRAW;
        }
        return GameResultReason.NATURAL_DEATH_DRAW;
    }

    private boolean bothUsersUsedLightningWithoutKill(List<GameAction> actions) {
        return actions.stream()
                .map(GameAction::getUserId)
                .distinct()
                .count() == GameRoom.MAX_PARTICIPANTS
                && actions.stream().noneMatch(GameAction::isKill);
    }

    private GameResultPayload.ActionSummary toActionSummary(GameAction action) {
        return new GameResultPayload.ActionSummary(
                action.getUserId(),
                action.getServerReceiveTimeMs(),
                action.getLightningTimeMs(),
                action.getStarCoreHpAtLightning(),
                GameRules.LIGHTNING_DAMAGE,
                Math.max(0, action.getStarCoreHpAtLightning() - GameRules.LIGHTNING_DAMAGE),
                action.isKill()
        );
    }
}
