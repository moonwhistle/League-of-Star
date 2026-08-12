package com.sang.leagueofstar.game.record.outbox;

import com.sang.leagueofstar.common.config.ClockConfig;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import com.sang.leagueofstar.domain.game.event.GameFinishedEvent;
import com.sang.leagueofstar.domain.game.repository.GameRoomRepository;
import com.sang.leagueofstar.domain.game.service.GameRoomResultResolver;
import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import com.sang.leagueofstar.domain.rank.repository.UserRankInfoRepository;
import com.sang.leagueofstar.domain.rank.service.RankCommandService;
import com.sang.leagueofstar.domain.rank.service.RankReadService;
import com.sang.leagueofstar.domain.record.repository.GameRecordRepository;
import com.sang.leagueofstar.domain.record.service.GameRecordRankSettlementService;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutbox;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutboxStatus;
import com.sang.leagueofstar.game.record.outbox.repository.GameSettlementOutboxRepository;
import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxClaimService;
import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxProcessor;
import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxStateService;
import com.sang.leagueofstar.game.record.outbox.service.GameSettlementOutboxWorker;
import com.sang.leagueofstar.game.record.service.FinishedGameMatchStatusCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        ClockConfig.class,
        RankReadService.class,
        RankCommandService.class,
        GameRoomResultResolver.class,
        GameRecordRankSettlementService.class,
        GameSettlementOutboxClaimService.class,
        GameSettlementOutboxStateService.class,
        GameSettlementOutboxProcessor.class,
        GameSettlementOutboxWorker.class
})
class GameSettlementOutboxEndToEndTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @Autowired
    private UserRankInfoRepository userRankInfoRepository;

    @Autowired
    private GameRecordRepository gameRecordRepository;

    @Autowired
    private GameSettlementOutboxRepository outboxRepository;

    @Autowired
    private GameSettlementOutboxWorker worker;

    @Autowired
    private Clock clock;

    @MockitoBean
    private FinishedGameMatchStatusCleanupService cleanupService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Outbox Worker가 실제 랭크 변경과 참가자 전적 2건 저장을 완료한다")
    void processOutbox_SettleRankAndRecords() {
        userRankInfoRepository.saveAll(List.of(
                UserRankInfo.builder().userId(FIRST_USER_ID).build(),
                UserRankInfo.builder().userId(SECOND_USER_ID).build()
        ));
        GameRoom gameRoom = saveFinishedGameRoom();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        GameSettlementOutbox outbox = outboxRepository.save(GameSettlementOutbox.create(
                GameFinishedEvent.create(gameRoom.getId()),
                now
        ));

        worker.processImmediately(outbox.getEventId());

        assertThat(gameRecordRepository.countByGameRoomId(gameRoom.getId())).isEqualTo(2L);
        assertThat(userRankInfoRepository.findByUserId(FIRST_USER_ID).orElseThrow().getTotalWins()).isEqualTo(1);
        assertThat(userRankInfoRepository.findByUserId(SECOND_USER_ID).orElseThrow().getTotalLosses()).isEqualTo(1);
        assertThat(outboxRepository.findById(outbox.getEventId()).orElseThrow().getStatus())
                .isEqualTo(GameSettlementOutboxStatus.SUCCESS);
    }

    private GameRoom saveFinishedGameRoom() {
        GameRoom gameRoom = GameRoom.builder()
                .durationSeconds(10)
                .scenarioData(GameScenario.of(List.of(new HpStep(0, GameRoom.DEFAULT_STAR_CORE_MAX_HP))))
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        gameRoom.start(LocalDateTime.now());
        gameRoom.finish(GameResult.PLAYER1_WIN, FIRST_USER_ID);
        return gameRoomRepository.save(gameRoom);
    }
}
