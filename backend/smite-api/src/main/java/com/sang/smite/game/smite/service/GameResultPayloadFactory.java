package com.sang.smite.game.smite.service;

import com.sang.smite.domain.game.domain.GameAction;
import com.sang.smite.domain.game.domain.GameRules;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.game.smite.domain.GameSmiteResultReason;
import com.sang.smite.game.smite.dto.GameResultPayload;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GameResultPayloadFactory {

    public GameResultPayload smiteKill(Long gameRoomId,
                                       GameResult result,
                                       Long winnerUserId,
                                       long finishedAt,
                                       List<GameAction> actions) {
        return new GameResultPayload(
                gameRoomId,
                result,
                winnerUserId,
                GameSmiteResultReason.SMITE_KILL.getCode(),
                finishedAt,
                actions.stream()
                        .map(this::toActionSummary)
                        .toList()
        );
    }

    public GameResultPayload currentResult(Long gameRoomId,
                                           GameResult result,
                                           Long winnerUserId,
                                           long finishedAt,
                                           List<GameAction> actions) {
        return new GameResultPayload(
                gameRoomId,
                result,
                winnerUserId,
                resolveReason(winnerUserId),
                finishedAt,
                actions.stream()
                        .map(this::toActionSummary)
                        .toList()
        );
    }

    private String resolveReason(Long winnerUserId) {
        if (winnerUserId == null) {
            return GameSmiteResultReason.BOTH_SMITES_USED_DRAW.getCode();
        }
        return GameSmiteResultReason.SMITE_KILL.getCode();
    }

    private GameResultPayload.ActionSummary toActionSummary(GameAction action) {
        return new GameResultPayload.ActionSummary(
                action.getUserId(),
                action.getServerReceiveTimeMs(),
                action.getSmiteTimeMs(),
                action.getDragonHpAtSmite(),
                GameRules.SMITE_DAMAGE,
                Math.max(0, action.getDragonHpAtSmite() - GameRules.SMITE_DAMAGE),
                action.isKill()
        );
    }
}
