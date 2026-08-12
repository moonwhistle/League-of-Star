package com.sang.leagueofstar.match.service;

import com.sang.leagueofstar.domain.game.service.GameRoomReadService;
import com.sang.leagueofstar.matching.command.MatchQueueCommandService;
import com.sang.leagueofstar.matching.common.exception.MatchingErrorCode;
import com.sang.leagueofstar.matching.common.exception.MatchingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 매칭 진입/취소 흐름을 조율하는 api 레이어 서비스입니다.
 *
 * <p>league-of-star-matching 모듈의 {@link MatchQueueCommandService}를 호출하기 전에
 * 유저의 진행 중 gameRoom 여부를 검증합니다.
 * JPA(league-of-star-core)와 Redis 도메인 로직(league-of-star-matching)의 경계를 이 클래스가 맡습니다.
 */
@Service
@RequiredArgsConstructor
public class MatchQueueService {

    private final MatchQueueCommandService matchService;
    private final GameRoomReadService gameRoomReadService;

    /**
     * 유저를 매칭 대기열에 진입시킵니다.
     * 진행 중인 gameRoom이 없는지 확인한 후 매칭 모듈에 위임합니다.
     *
     * @param userId 진입 요청한 유저의 ID
     */
    public void joinQueue(Long userId) {
        validateNoActiveGameRoom(userId);
        matchService.joinQueue(userId);
    }

    /**
     * 유저를 매칭 대기열에서 취소(이탈)시킵니다.
     * 단일 FIFO 대기열에서 유저를 제거하도록 매칭 모듈에 위임합니다.
     *
     * @param userId 취소 요청한 유저의 ID
     */
    public void leaveQueue(Long userId) {
        matchService.leaveQueue(userId);
    }

    private void validateNoActiveGameRoom(Long userId) {
        if (gameRoomReadService.existsActiveGameRoomByUserId(userId)) {
            throw new MatchingException(MatchingErrorCode.ACTIVE_GAME_ROOM_EXISTS);
        }
    }
}
