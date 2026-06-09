package com.sang.leagueofstar.matching.common.exception;

import com.sang.leagueofstar.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 매칭 모듈에서 발생하는 공통 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum MatchingErrorCode implements BaseErrorCode {

    MATCH_REDIS_FETCH_ERROR(500, "MATCH_001", "Redis 매칭 대기열 조회 중 오류가 발생했습니다."),
    MATCH_LUA_SCRIPT_ERROR(500, "MATCH_002", "매칭 루아 스크립트 실행 또는 로드 중 오류가 발생했습니다."),
    ALREADY_IN_QUEUE(409, "MATCH_003", "이미 매칭 대기열에 진입한 유저입니다."),
    NOT_IN_QUEUE(400, "MATCH_004", "매칭 대기열에 진입하지 않은 유저입니다."),
    MATCH_QUEUE_ADD_ERROR(500, "MATCH_005", "매칭 대기열 추가 중 오류가 발생했습니다."),
    MATCH_SESSION_EXPIRED(410, "MATCH_006", "매칭 수락 시간이 만료되었습니다."),
    MATCH_SESSION_NOT_PARTICIPANT(403, "MATCH_007", "해당 매칭의 참여자가 아닙니다."),
    MATCH_SESSION_ALREADY_ACCEPTED(409, "MATCH_008", "이미 수락한 매칭입니다."),
    MATCH_SESSION_ALREADY_COMPLETED(409, "MATCH_009", "이미 완료된 매칭입니다."),
    MATCH_SESSION_ALREADY_DECLINED(409, "MATCH_010", "이미 거절된 매칭입니다."),
    MATCH_SESSION_TIMEOUT(409, "MATCH_011", "이미 응답 시간이 초과된 매칭입니다."),
    MATCH_RESPONSE_LOCK_FAILED(409, "MATCH_012", "매칭 응답 처리 중입니다. 잠시 후 다시 시도해주세요."),
    ACTIVE_GAME_ROOM_EXISTS(409, "MATCH_013", "진행 중인 게임이 있어 매칭 큐에 진입할 수 없습니다.");

    private final int httpStatus;
    private final String customCode;
    private final String message;

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String customCode() {
        return customCode;
    }

    @Override
    public String message() {
        return message;
    }
}
