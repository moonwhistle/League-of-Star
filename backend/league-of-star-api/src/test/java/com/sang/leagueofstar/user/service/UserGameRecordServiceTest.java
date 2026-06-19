package com.sang.leagueofstar.user.service;

import com.sang.leagueofstar.common.exception.CoreErrorCode;
import com.sang.leagueofstar.common.exception.CoreException;
import com.sang.leagueofstar.domain.rank.domain.vo.Division;
import com.sang.leagueofstar.domain.rank.domain.vo.Rank;
import com.sang.leagueofstar.domain.rank.domain.vo.Tier;
import com.sang.leagueofstar.domain.record.domain.GameRecord;
import com.sang.leagueofstar.domain.record.domain.vo.GameRecordResult;
import com.sang.leagueofstar.domain.record.service.GameRecordReadService;
import com.sang.leagueofstar.domain.user.domain.User;
import com.sang.leagueofstar.domain.user.service.UserReadService;
import com.sang.leagueofstar.user.controller.response.UserGameRecordListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserGameRecordServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FIRST_OPPONENT_ID = 2L;
    private static final Long SECOND_OPPONENT_ID = 3L;
    private static final LocalDateTime PLAYED_AT = LocalDateTime.of(2026, 6, 19, 10, 30);

    @InjectMocks
    private UserGameRecordService userGameRecordService;

    @Mock
    private UserReadService userReadService;

    @Mock
    private GameRecordReadService gameRecordReadService;

    @Test
    @DisplayName("getMyGameRecords - 전적 목록을 응답으로 변환하고 opponent는 batch 조회한다")
    void getMyGameRecords_ReturnResponseWithBatchOpponentLookup() {
        // given
        GameRecord firstRecord = gameRecord(100L, USER_ID, FIRST_OPPONENT_ID, GameRecordResult.WIN, 80, 105);
        GameRecord secondRecord = gameRecord(101L, USER_ID, SECOND_OPPONENT_ID, GameRecordResult.LOSS, 105, 80);

        given(userReadService.findById(USER_ID)).willReturn(user(USER_ID, "Me"));
        given(gameRecordReadService.findRecentByUserId(USER_ID, PageRequest.of(1, UserGameRecordService.PAGE_SIZE)))
                .willReturn(List.of(firstRecord, secondRecord));
        given(gameRecordReadService.countByUserId(USER_ID)).willReturn(42L);
        given(userReadService.findAllByIdsOrThrow(anyCollection()))
                .willReturn(List.of(user(FIRST_OPPONENT_ID, "ShadowWalker"), user(SECOND_OPPONENT_ID, "LegendaryStar")));

        // when
        UserGameRecordListResponse response = userGameRecordService.getMyGameRecords(USER_ID, 2);

        // then
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.totalElements()).isEqualTo(30);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.records()).hasSize(2);
        assertThat(response.records().get(0).gameId()).isEqualTo(100L);
        assertThat(response.records().get(0).result()).isEqualTo(GameRecordResult.WIN);
        assertThat(response.records().get(0).opponentNickname()).isEqualTo("ShadowWalker");
        assertThat(response.records().get(0).rankBefore()).isEqualTo("GOLD_IV");
        assertThat(response.records().get(0).rankAfter()).isEqualTo("GOLD_III");
        assertThat(response.records().get(0).lpChange()).isEqualTo(25);
        assertThat(response.records().get(0).playedAt()).isEqualTo(PLAYED_AT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> opponentIdsCaptor =
                (ArgumentCaptor<Collection<Long>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Collection.class);
        then(userReadService).should(times(1)).findAllByIdsOrThrow(opponentIdsCaptor.capture());
        assertThat(opponentIdsCaptor.getValue()).containsExactly(FIRST_OPPONENT_ID, SECOND_OPPONENT_ID);
        then(userReadService).should(never()).findById(FIRST_OPPONENT_ID);
        then(userReadService).should(never()).findById(SECOND_OPPONENT_ID);
    }

    @Test
    @DisplayName("getMyGameRecords - 전적이 없으면 empty response를 반환한다")
    void getMyGameRecords_ReturnEmptyResponse() {
        // given
        given(userReadService.findById(USER_ID)).willReturn(user(USER_ID, "Me"));
        given(gameRecordReadService.findRecentByUserId(USER_ID, PageRequest.of(0, UserGameRecordService.PAGE_SIZE)))
                .willReturn(List.of());
        given(gameRecordReadService.countByUserId(USER_ID)).willReturn(0L);
        given(userReadService.findAllByIdsOrThrow(anyCollection())).willReturn(List.of());

        // when
        UserGameRecordListResponse response = userGameRecordService.getMyGameRecords(USER_ID, 1);

        // then
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalPages()).isZero();
        assertThat(response.totalElements()).isZero();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.records()).isEmpty();
    }

    @Test
    @DisplayName("getMyGameRecords - user가 없으면 전적 조회를 하지 않고 USER_NOT_FOUND를 전달한다")
    void getMyGameRecords_UserNotFound() {
        // given
        given(userReadService.findById(USER_ID)).willThrow(new CoreException(CoreErrorCode.USER_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> userGameRecordService.getMyGameRecords(USER_ID, 1))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CoreErrorCode.USER_NOT_FOUND));

        then(gameRecordReadService).should(never()).findRecentByUserId(USER_ID, PageRequest.of(0, UserGameRecordService.PAGE_SIZE));
        then(gameRecordReadService).should(never()).countByUserId(USER_ID);
    }

    private GameRecord gameRecord(
            Long gameRoomId,
            Long userId,
            Long opponentId,
            GameRecordResult result,
            int lpBefore,
            int lpAfter
    ) {
        GameRecord record = GameRecord.create(
                gameRoomId,
                userId,
                opponentId,
                null,
                null,
                result,
                lpBefore,
                lpAfter,
                Rank.of(Tier.GOLD, Division.IV),
                Rank.of(Tier.GOLD, Division.III)
        );
        ReflectionTestUtils.setField(record, "createdAt", PLAYED_AT);
        return record;
    }

    private User user(Long userId, String nickname) {
        return User.builder()
                .id(userId)
                .email("user" + userId + "@example.com")
                .nickname(nickname)
                .build();
    }
}
