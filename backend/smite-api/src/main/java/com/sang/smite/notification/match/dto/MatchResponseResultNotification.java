package com.sang.smite.notification.match.dto;

/**
 * matchId 단위 매칭 수락/거절/timeout 최종 결과 SSE payload입니다.
 *
 * <p>HTTP accept/reject 응답은 command ack만 담당하고, 최종 화면 전환은 이 이벤트의 action을 기준으로 처리합니다.</p>
 */
public record MatchResponseResultNotification(
        String matchId,
        MatchResponseOutcome outcome,
        MatchResponseReason reason,
        MatchResponseAction action,
        Opponent opponent,
        Game game
) {

    public record Opponent(
            Long userId,
            String nickname,
            String tier,
            int tierScore
    ) {
    }

    /**
     * 게임 대기 화면 진입에 필요한 payload입니다.
     */
    public record Game(
            Long gameRoomId,
            String videoUrl,
            String webSocketUrl
    ) {
    }
}
