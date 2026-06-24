package com.sang.leagueofstar.game.result.service;

import com.sang.leagueofstar.domain.game.domain.GameAction;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.GameRules;
import com.sang.leagueofstar.domain.game.domain.vo.GameMode;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.game.result.domain.GameResultReason;
import com.sang.leagueofstar.game.result.domain.PracticeResult;
import com.sang.leagueofstar.game.result.dto.GameResultPayload;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GameResultPayloadFactory {

    public GameResultPayload lightningKill(Long gameRoomId,
                                           GameRoom gameRoom,
                                           long finishedAt,
                                           List<GameAction> actions) {
        return create(
                gameRoomId,
                gameRoom.getGameMode(),
                gameRoom.getResult(),
                gameRoom.getWinnerId(),
                GameResultReason.LIGHTNING_KILL,
                null,
                finishedAt,
                actions
        );
    }

    public GameResultPayload practiceLightningKill(Long gameRoomId,
                                                   GameResult result,
                                                   Long winnerUserId,
                                                   long finishedAt,
                                                   List<GameAction> actions) {
        return create(
                gameRoomId,
                GameMode.PRACTICE,
                result,
                winnerUserId,
                GameResultReason.PRACTICE_LIGHTNING_KILL,
                PracticeResult.SUCCESS,
                finishedAt,
                actions
        );
    }

    public GameResultPayload naturalDeathDraw(Long gameRoomId,
                                              GameRoom gameRoom,
                                              long finishedAt,
                                              List<GameAction> actions) {
        return create(
                gameRoomId,
                gameRoom.getGameMode(),
                gameRoom.getResult(),
                gameRoom.getWinnerId(),
                GameResultReason.NATURAL_DEATH_DRAW,
                null,
                finishedAt,
                actions
        );
    }

    public GameResultPayload practiceTimeout(Long gameRoomId,
                                             GameResult result,
                                             Long winnerUserId,
                                             long finishedAt,
                                             List<GameAction> actions) {
        return create(
                gameRoomId,
                GameMode.PRACTICE,
                result,
                winnerUserId,
                GameResultReason.PRACTICE_TIMEOUT,
                PracticeResult.FAILED,
                finishedAt,
                actions
        );
    }

    public GameResultPayload currentResult(Long gameRoomId,
                                           GameRoom gameRoom,
                                           long finishedAt,
                                           List<GameAction> actions) {
        CurrentResultPolicy policy = resolveCurrentResultPolicy(gameRoom);
        return create(
                gameRoomId,
                gameRoom.getGameMode(),
                gameRoom.getResult(),
                gameRoom.getWinnerId(),
                policy.reason(),
                policy.practiceResult(),
                finishedAt,
                actions
        );
    }

    private GameResultPayload create(Long gameRoomId,
                                     GameMode gameMode,
                                     GameResult result,
                                     Long winnerUserId,
                                     GameResultReason reason,
                                     PracticeResult practiceResult,
                                     long finishedAt,
                                     List<GameAction> actions) {
        return new GameResultPayload(
                gameRoomId,
                gameMode,
                result,
                winnerUserId,
                reason.getCode(),
                practiceResult,
                finishedAt,
                actions.stream()
                        .map(this::toActionSummary)
                        .toList()
        );
    }

    private CurrentResultPolicy resolveCurrentResultPolicy(GameRoom gameRoom) {
        if (gameRoom.isPracticeMode()) {
            if (gameRoom.getWinnerId() != null) {
                return new CurrentResultPolicy(GameResultReason.PRACTICE_LIGHTNING_KILL, PracticeResult.SUCCESS);
            }
            return new CurrentResultPolicy(GameResultReason.PRACTICE_TIMEOUT, PracticeResult.FAILED);
        }
        if (gameRoom.getWinnerId() != null) {
            return new CurrentResultPolicy(GameResultReason.LIGHTNING_KILL, null);
        }
        return new CurrentResultPolicy(GameResultReason.NATURAL_DEATH_DRAW, null);
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

    private record CurrentResultPolicy(
            GameResultReason reason,
            PracticeResult practiceResult
    ) {
    }
}
