package com.sang.smite.game.summary.service;

import com.sang.smite.common.exception.ApiErrorCode;
import com.sang.smite.common.exception.ApiException;
import com.sang.smite.common.exception.CoreErrorCode;
import com.sang.smite.common.exception.CoreException;
import com.sang.smite.domain.game.domain.vo.GameResult;
import com.sang.smite.domain.game.domain.vo.GameStatus;
import com.sang.smite.domain.game.service.GameRoomReadService;
import com.sang.smite.domain.game.service.dto.GameRoomSummaryReadModel;
import com.sang.smite.domain.rank.domain.vo.Division;
import com.sang.smite.domain.rank.domain.vo.Rank;
import com.sang.smite.domain.rank.domain.vo.Tier;
import com.sang.smite.domain.record.domain.GameRecord;
import com.sang.smite.domain.record.domain.vo.GameRecordResult;
import com.sang.smite.domain.record.domain.vo.GameRecordSeriesType;
import com.sang.smite.domain.record.service.GameRecordReadService;
import com.sang.smite.domain.user.domain.User;
import com.sang.smite.domain.user.service.UserReadService;
import com.sang.smite.game.summary.controller.response.GameSummaryDoneResponse;
import com.sang.smite.game.summary.controller.response.GameSummaryPendingResponse;
import com.sang.smite.game.summary.controller.response.GameSummaryResponse;
import com.sang.smite.game.summary.controller.response.GameSummaryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameSummaryServiceTest {

    private static final Long GAME_ID = 100L;
    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final Long UNKNOWN_USER_ID = 999L;
    private static final LocalDateTime FINISHED_AT = LocalDateTime.of(2026, 5, 27, 12, 0);

    @InjectMocks
    private GameSummaryService gameSummaryService;

    @Mock
    private GameRoomReadService gameRoomReadService;

    @Mock
    private GameRecordReadService gameRecordReadService;

    @Mock
    private UserReadService userReadService;

    @Test
    @DisplayName("getSummary - FINISHED이고 record 2행이면 DONE summary를 반환한다")
    void getSummary_FinishedAndSettled_ReturnDone() {
        // given
        given(gameRoomReadService.getSummaryReadModel(GAME_ID)).willReturn(finishedRoom(
                GameResult.PLAYER1_WIN,
                FIRST_USER_ID
        ));
        given(gameRecordReadService.countByGameRoomId(GAME_ID)).willReturn(2L);
        given(gameRecordReadService.findByGameRoomId(GAME_ID)).willReturn(List.of(
                record(FIRST_USER_ID, SECOND_USER_ID, GameRecordResult.WIN, 40, 56,
                        rank(Tier.GOLD, Division.IV), rank(Tier.GOLD, Division.III)),
                record(SECOND_USER_ID, FIRST_USER_ID, GameRecordResult.LOSS, 61, 45,
                        rank(Tier.GOLD, Division.IV), rank(Tier.GOLD, Division.IV))
        ));
        given(userReadService.findByIds(List.of(FIRST_USER_ID, SECOND_USER_ID))).willReturn(List.of(
                user(FIRST_USER_ID, "moon"),
                user(SECOND_USER_ID, "other")
        ));

        // when
        GameSummaryResponse response = gameSummaryService.getSummary(GAME_ID, FIRST_USER_ID);

        // then
        assertThat(response).isInstanceOf(GameSummaryDoneResponse.class);
        GameSummaryDoneResponse done = (GameSummaryDoneResponse) response;
        assertThat(done.summaryStatus()).isEqualTo(GameSummaryStatus.DONE);
        assertThat(done.gameId()).isEqualTo(GAME_ID);
        assertThat(done.gameResult()).isEqualTo(GameResult.PLAYER1_WIN);
        assertThat(done.winnerUserId()).isEqualTo(FIRST_USER_ID);
        assertThat(done.finishedAt()).isEqualTo(FINISHED_AT);
        assertThat(done.me().userId()).isEqualTo(FIRST_USER_ID);
        assertThat(done.me().nickname()).isEqualTo("moon");
        assertThat(done.me().result()).isEqualTo(GameRecordResult.WIN);
        assertThat(done.me().lpBefore()).isEqualTo(40);
        assertThat(done.me().lpAfter()).isEqualTo(56);
        assertThat(done.me().lpChange()).isEqualTo(16);
        assertThat(done.me().rankBefore()).isEqualTo("GOLD_IV");
        assertThat(done.me().rankAfter()).isEqualTo("GOLD_III");
        assertThat(done.me().seriesType()).isEqualTo(GameRecordSeriesType.RANK);
        assertThat(done.me().rankSeriesId()).isNull();
        assertThat(done.opponent().userId()).isEqualTo(SECOND_USER_ID);
        assertThat(done.opponent().nickname()).isEqualTo("other");
        assertThat(done.opponent().result()).isEqualTo(GameRecordResult.LOSS);
    }

    @Test
    @DisplayName("getSummary - Apex rank는 tier 문자열만 반환한다")
    void getSummary_ApexRank_ReturnTierOnly() {
        // given
        given(gameRoomReadService.getSummaryReadModel(GAME_ID)).willReturn(finishedRoom(
                GameResult.DRAW,
                null
        ));
        given(gameRecordReadService.countByGameRoomId(GAME_ID)).willReturn(2L);
        given(gameRecordReadService.findByGameRoomId(GAME_ID)).willReturn(List.of(
                record(FIRST_USER_ID, SECOND_USER_ID, GameRecordResult.DRAW, 500, 500,
                        rank(Tier.MASTER, null), rank(Tier.MASTER, null)),
                record(SECOND_USER_ID, FIRST_USER_ID, GameRecordResult.DRAW, 500, 500,
                        rank(Tier.GRANDMASTER, null), rank(Tier.GRANDMASTER, null))
        ));
        given(userReadService.findByIds(List.of(FIRST_USER_ID, SECOND_USER_ID))).willReturn(List.of(
                user(FIRST_USER_ID, "moon"),
                user(SECOND_USER_ID, "other")
        ));

        // when
        GameSummaryDoneResponse response = (GameSummaryDoneResponse) gameSummaryService.getSummary(GAME_ID, FIRST_USER_ID);

        // then
        assertThat(response.winnerUserId()).isNull();
        assertThat(response.me().rankBefore()).isEqualTo("MASTER");
        assertThat(response.opponent().rankBefore()).isEqualTo("GRANDMASTER");
    }

    @Test
    @DisplayName("getSummary - record count 0이면 PENDING을 반환한다")
    void getSummary_RecordCountZero_ReturnPending() {
        // given
        given(gameRoomReadService.getSummaryReadModel(GAME_ID)).willReturn(finishedRoom(
                GameResult.PLAYER1_WIN,
                FIRST_USER_ID
        ));
        given(gameRecordReadService.countByGameRoomId(GAME_ID)).willReturn(0L);

        // when
        GameSummaryResponse response = gameSummaryService.getSummary(GAME_ID, FIRST_USER_ID);

        // then
        assertPending(response);
        verify(gameRecordReadService, never()).findByGameRoomId(GAME_ID);
        verify(userReadService, never()).findByIds(List.of(FIRST_USER_ID, SECOND_USER_ID));
    }

    @Test
    @DisplayName("getSummary - record count 1이면 PENDING을 반환한다")
    void getSummary_RecordCountOne_ReturnPending() {
        // given
        given(gameRoomReadService.getSummaryReadModel(GAME_ID)).willReturn(finishedRoom(
                GameResult.PLAYER1_WIN,
                FIRST_USER_ID
        ));
        given(gameRecordReadService.countByGameRoomId(GAME_ID)).willReturn(1L);

        // when
        GameSummaryResponse response = gameSummaryService.getSummary(GAME_ID, FIRST_USER_ID);

        // then
        assertPending(response);
        verify(gameRecordReadService, never()).findByGameRoomId(GAME_ID);
    }

    @Test
    @DisplayName("getSummary - 참가자가 아니면 403 예외를 던진다")
    void getSummary_NotParticipant_ThrowForbidden() {
        // given
        given(gameRoomReadService.getSummaryReadModel(GAME_ID)).willReturn(finishedRoom(
                GameResult.PLAYER1_WIN,
                FIRST_USER_ID
        ));

        // when & then
        assertThatThrownBy(() -> gameSummaryService.getSummary(GAME_ID, UNKNOWN_USER_ID))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_FORBIDDEN));
        verify(gameRecordReadService, never()).countByGameRoomId(GAME_ID);
    }

    @Test
    @DisplayName("getSummary - FINISHED가 아니면 409 예외를 던진다")
    void getSummary_NotFinished_ThrowConflict() {
        // given
        given(gameRoomReadService.getSummaryReadModel(GAME_ID)).willReturn(new GameRoomSummaryReadModel(
                GAME_ID,
                GameStatus.IN_PROGRESS,
                null,
                null,
                null,
                List.of(FIRST_USER_ID, SECOND_USER_ID)
        ));

        // when & then
        assertThatThrownBy(() -> gameSummaryService.getSummary(GAME_ID, FIRST_USER_ID))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.GAME_SUMMARY_NOT_FINISHED));
        verify(gameRecordReadService, never()).countByGameRoomId(GAME_ID);
    }

    @Test
    @DisplayName("getSummary - record count가 2보다 크면 정합성 예외를 던진다")
    void getSummary_RecordCountGreaterThanTwo_ThrowConflict() {
        // given
        given(gameRoomReadService.getSummaryReadModel(GAME_ID)).willReturn(finishedRoom(
                GameResult.PLAYER1_WIN,
                FIRST_USER_ID
        ));
        given(gameRecordReadService.countByGameRoomId(GAME_ID)).willReturn(3L);

        // when & then
        assertThatThrownBy(() -> gameSummaryService.getSummary(GAME_ID, FIRST_USER_ID))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.GAME_SUMMARY_INVALID_RECORD_STATE));
    }

    @Test
    @DisplayName("getSummary - record userIds와 participant userIds가 다르면 정합성 예외를 던진다")
    void getSummary_RecordUsersMismatch_ThrowConflict() {
        // given
        given(gameRoomReadService.getSummaryReadModel(GAME_ID)).willReturn(finishedRoom(
                GameResult.PLAYER1_WIN,
                FIRST_USER_ID
        ));
        given(gameRecordReadService.countByGameRoomId(GAME_ID)).willReturn(2L);
        given(gameRecordReadService.findByGameRoomId(GAME_ID)).willReturn(List.of(
                record(FIRST_USER_ID, SECOND_USER_ID, GameRecordResult.WIN, 40, 56,
                        rank(Tier.GOLD, Division.IV), rank(Tier.GOLD, Division.III)),
                record(UNKNOWN_USER_ID, FIRST_USER_ID, GameRecordResult.LOSS, 61, 45,
                        rank(Tier.GOLD, Division.IV), rank(Tier.GOLD, Division.IV))
        ));

        // when & then
        assertThatThrownBy(() -> gameSummaryService.getSummary(GAME_ID, FIRST_USER_ID))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.GAME_SUMMARY_INVALID_RECORD_STATE));
        verify(userReadService, never()).findByIds(List.of(FIRST_USER_ID, SECOND_USER_ID));
    }

    @Test
    @DisplayName("getSummary - 참가자 user 조회 결과가 누락되면 USER_NOT_FOUND 예외를 던진다")
    void getSummary_UserMissing_ThrowUserNotFound() {
        // given
        given(gameRoomReadService.getSummaryReadModel(GAME_ID)).willReturn(finishedRoom(
                GameResult.PLAYER1_WIN,
                FIRST_USER_ID
        ));
        given(gameRecordReadService.countByGameRoomId(GAME_ID)).willReturn(2L);
        given(gameRecordReadService.findByGameRoomId(GAME_ID)).willReturn(List.of(
                record(FIRST_USER_ID, SECOND_USER_ID, GameRecordResult.WIN, 40, 56,
                        rank(Tier.GOLD, Division.IV), rank(Tier.GOLD, Division.III)),
                record(SECOND_USER_ID, FIRST_USER_ID, GameRecordResult.LOSS, 61, 45,
                        rank(Tier.GOLD, Division.IV), rank(Tier.GOLD, Division.IV))
        ));
        given(userReadService.findByIds(List.of(FIRST_USER_ID, SECOND_USER_ID))).willReturn(List.of(
                user(FIRST_USER_ID, "moon")
        ));

        // when & then
        assertThatThrownBy(() -> gameSummaryService.getSummary(GAME_ID, FIRST_USER_ID))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.USER_NOT_FOUND));
    }

    private void assertPending(GameSummaryResponse response) {
        assertThat(response).isInstanceOf(GameSummaryPendingResponse.class);
        GameSummaryPendingResponse pending = (GameSummaryPendingResponse) response;
        assertThat(pending.summaryStatus()).isEqualTo(GameSummaryStatus.PENDING);
        assertThat(pending.gameId()).isEqualTo(GAME_ID);
        assertThat(pending.retryAfterMillis()).isEqualTo(1_000L);
    }

    private GameRoomSummaryReadModel finishedRoom(GameResult result, Long winnerId) {
        return new GameRoomSummaryReadModel(
                GAME_ID,
                GameStatus.FINISHED,
                result,
                winnerId,
                FINISHED_AT,
                List.of(FIRST_USER_ID, SECOND_USER_ID)
        );
    }

    private GameRecord record(
            Long userId,
            Long opponentId,
            GameRecordResult result,
            int lpBefore,
            int lpAfter,
            Rank rankBefore,
            Rank rankAfter
    ) {
        return GameRecord.create(
                GAME_ID,
                userId,
                opponentId,
                null,
                GameRecordSeriesType.RANK,
                result,
                lpBefore,
                lpAfter,
                rankBefore,
                rankAfter
        );
    }

    private Rank rank(Tier tier, Division division) {
        return Rank.of(tier, division);
    }

    private User user(Long userId, String nickname) {
        return User.builder()
                .id(userId)
                .email(nickname + "@example.com")
                .nickname(nickname)
                .build();
    }
}
