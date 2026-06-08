package com.sang.leagueofstar.domain.game.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.game.domain.GameParticipant;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameParticipantResult;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.domain.game.service.dto.GameRoomParticipantResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class GameRoomResultResolver {

    /**
     * FINISHED gameRoom의 종료 결과를 participant 순서 기준의 참가자별 결과로 해석합니다.
     */
    public List<GameRoomParticipantResult> resolveParticipantResults(GameRoom gameRoom) {
        validateFinishedGameRoom(gameRoom);
        List<GameParticipant> participants = validateAndGetParticipants(gameRoom);
        GameParticipant firstParticipant = participants.get(0);
        GameParticipant secondParticipant = participants.get(1);

        if (gameRoom.getResult() == GameResult.PLAYER1_WIN) {
            validateWinner(gameRoom, firstParticipant);
            return List.of(
                    resultOf(firstParticipant, secondParticipant, GameParticipantResult.WIN),
                    resultOf(secondParticipant, firstParticipant, GameParticipantResult.LOSS)
            );
        }
        if (gameRoom.getResult() == GameResult.PLAYER2_WIN) {
            validateWinner(gameRoom, secondParticipant);
            return List.of(
                    resultOf(firstParticipant, secondParticipant, GameParticipantResult.LOSS),
                    resultOf(secondParticipant, firstParticipant, GameParticipantResult.WIN)
            );
        }

        validateDraw(gameRoom);
        return List.of(
                resultOf(firstParticipant, secondParticipant, GameParticipantResult.DRAW),
                resultOf(secondParticipant, firstParticipant, GameParticipantResult.DRAW)
        );
    }

    private void validateFinishedGameRoom(GameRoom gameRoom) {
        if (!gameRoom.getStatus().isFinished() || gameRoom.getResult() == null) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_STATE);
        }
    }

    private List<GameParticipant> validateAndGetParticipants(GameRoom gameRoom) {
        List<GameParticipant> participants = gameRoom.getParticipants();
        if (participants.size() != GameRoom.MAX_PARTICIPANTS) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_PARTICIPANTS);
        }

        Long firstUserId = participants.get(0).getUserId();
        Long secondUserId = participants.get(1).getUserId();
        if (firstUserId == null || secondUserId == null || Objects.equals(firstUserId, secondUserId)) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_PARTICIPANTS);
        }
        return participants;
    }

    private void validateWinner(GameRoom gameRoom, GameParticipant expectedWinner) {
        if (!Objects.equals(gameRoom.getWinnerId(), expectedWinner.getUserId())) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_STATE);
        }
    }

    private void validateDraw(GameRoom gameRoom) {
        if (gameRoom.getWinnerId() != null) {
            throw new CoreException(CoreErrorCode.INVALID_GAME_STATE);
        }
    }

    private GameRoomParticipantResult resultOf(
            GameParticipant participant,
            GameParticipant opponent,
            GameParticipantResult result
    ) {
        return new GameRoomParticipantResult(participant.getUserId(), opponent.getUserId(), result);
    }
}
