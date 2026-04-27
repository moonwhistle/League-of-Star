package com.sang.smite.domain.rank.service;

import com.sang.smite.domain.rank.domain.UserRankInfo;
import com.sang.smite.domain.rank.repository.UserRankInfoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RankCommandServiceTest {

    @InjectMocks
    private RankCommandService rankCommandService;

    @Mock
    private UserRankInfoRepository userRankInfoRepository;

    @Test
    @DisplayName("initializeRank - 초기 랭크 정보를 생성하고 저장한다")
    void initializeRank_Success() {
        // given
        Long userId = 1L;

        // when
        rankCommandService.initializeRank(userId);

        // then
        ArgumentCaptor<UserRankInfo> rankInfoCaptor = ArgumentCaptor.forClass(UserRankInfo.class);
        verify(userRankInfoRepository, times(1)).save(rankInfoCaptor.capture());

        UserRankInfo capturedRankInfo = rankInfoCaptor.getValue();
        assertThat(capturedRankInfo.getUserId()).isEqualTo(userId);
        assertThat(capturedRankInfo.getLp()).isZero();
        assertThat(capturedRankInfo.isInPlacement()).isTrue();
    }
}
