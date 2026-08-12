package com.sang.leagueofstar.game.record.outbox;

import com.sang.leagueofstar.common.config.ClockConfig;
import com.sang.leagueofstar.domain.game.domain.GameRoom;
import com.sang.leagueofstar.domain.game.domain.vo.GameResult;
import com.sang.leagueofstar.domain.game.domain.vo.GameScenario;
import com.sang.leagueofstar.domain.game.domain.vo.GameStatus;
import com.sang.leagueofstar.domain.game.domain.vo.HpStep;
import com.sang.leagueofstar.domain.game.repository.GameRoomRepository;
import com.sang.leagueofstar.domain.game.service.GameRoomCommandService;
import com.sang.leagueofstar.domain.game.service.GameRoomResultResolver;
import com.sang.leagueofstar.domain.game.service.GameScenarioGenerator;
import com.sang.leagueofstar.domain.rank.domain.UserRankInfo;
import com.sang.leagueofstar.domain.rank.repository.UserRankInfoRepository;
import com.sang.leagueofstar.domain.rank.service.RankCommandService;
import com.sang.leagueofstar.domain.rank.service.RankReadService;
import com.sang.leagueofstar.domain.record.repository.GameRecordRepository;
import com.sang.leagueofstar.domain.record.service.GameRecordRankSettlementService;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutbox;
import com.sang.leagueofstar.game.record.outbox.domain.GameSettlementOutboxStatus;
import com.sang.leagueofstar.game.record.outbox.listener.GameSettlementOutboxEventListener;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        ClockConfig.class,
        GameScenarioGenerator.class,
        GameRoomCommandService.class,
        GameSettlementOutboxEventListener.class,
        RankReadService.class,
        RankCommandService.class,
        GameRoomResultResolver.class,
        GameRecordRankSettlementService.class,
        GameSettlementOutboxClaimService.class,
        GameSettlementOutboxStateService.class,
        GameSettlementOutboxProcessor.class,
        GameSettlementOutboxWorker.class
})
class GameSettlementFailureWindowTest {

    private static final Long FIRST_USER_ID = 1L;
    private static final Long SECOND_USER_ID = 2L;
    private static final String MYSQL_URL = System.getenv("OUTBOX_FAILURE_TEST_DATASOURCE_URL");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        if (MYSQL_URL == null) {
            registry.add(
                    "spring.datasource.url",
                    () -> "jdbc:h2:mem:outbox-failure-window;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
            );
            return;
        }

        registry.add("spring.datasource.url", () -> MYSQL_URL);
        registry.add("spring.datasource.username", () -> System.getenv("OUTBOX_FAILURE_TEST_DATASOURCE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("OUTBOX_FAILURE_TEST_DATASOURCE_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @Autowired
    private GameRoomCommandService gameRoomCommandService;

    @Autowired
    private UserRankInfoRepository userRankInfoRepository;

    @Autowired
    private GameRecordRepository gameRecordRepository;

    @Autowired
    private GameRecordRankSettlementService settlementService;

    @Autowired
    private GameSettlementOutboxRepository outboxRepository;

    @MockitoSpyBean
    private GameSettlementOutboxWorker outboxWorker;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private FinishedGameMatchStatusCleanupService cleanupService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("commit 직후 장애에서 직접 호출은 유실되고 Outbox는 작업을 복구한다")
    void crashAfterCommit_CompareLegacyCallbackAndOutbox() {
        doNothing().when(outboxWorker).processImmediately(anyString());
        userRankInfoRepository.saveAll(List.of(
                UserRankInfo.builder().userId(FIRST_USER_ID).build(),
                UserRankInfo.builder().userId(SECOND_USER_ID).build()
        ));

        Long legacyGameRoomId = saveInProgressGameRoom();
        AtomicBoolean legacySettlementInvoked = new AtomicBoolean(false);

        // 기존 방식은 정산 작업을 저장하지 않고 afterCommit callback으로만 전달했다.
        assertThatThrownBy(() -> transactionTemplate().executeWithoutResult(status -> {
            TransactionSynchronizationManager.registerSynchronization(simulatedCrash());
            GameRoom gameRoom = gameRoomRepository.findById(legacyGameRoomId).orElseThrow();
            gameRoom.finish(GameResult.PLAYER1_WIN, FIRST_USER_ID);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    legacySettlementInvoked.set(true);
                    settlementService.settleFinishedGameRoom(legacyGameRoomId);
                }
            });
        })).isInstanceOf(SimulatedProcessCrashException.class);

        assertThat(gameRoomRepository.findById(legacyGameRoomId).orElseThrow().getStatus())
                .isEqualTo(GameStatus.FINISHED);
        assertThat(legacySettlementInvoked).isFalse();
        assertThat(gameRecordRepository.countByGameRoomId(legacyGameRoomId)).isZero();
        assertThat(outboxRepository.existsByGameRoomId(legacyGameRoomId)).isFalse();

        Long outboxGameRoomId = saveInProgressGameRoom();

        assertThatThrownBy(() -> transactionTemplate().executeWithoutResult(status -> {
            TransactionSynchronizationManager.registerSynchronization(simulatedCrash());
            gameRoomCommandService.finishInProgressRoomByLightningKill(outboxGameRoomId, FIRST_USER_ID);
        })).isInstanceOf(SimulatedProcessCrashException.class);

        GameSettlementOutbox outbox = outboxRepository.findAll().get(0);
        assertThat(gameRoomRepository.findById(outboxGameRoomId).orElseThrow().getStatus())
                .isEqualTo(GameStatus.FINISHED);
        assertThat(gameRecordRepository.countByGameRoomId(outboxGameRoomId)).isZero();
        assertThat(outbox.getGameRoomId()).isEqualTo(outboxGameRoomId);
        assertThat(outbox.getStatus()).isEqualTo(GameSettlementOutboxStatus.INIT);
        verify(outboxWorker).processImmediately(outbox.getEventId());

        assertThat(outboxWorker.processPendingBatch()).isEqualTo(1);

        assertThat(gameRecordRepository.countByGameRoomId(outboxGameRoomId)).isEqualTo(2L);
        assertThat(outboxRepository.findById(outbox.getEventId()).orElseThrow().getStatus())
                .isEqualTo(GameSettlementOutboxStatus.SUCCESS);
    }

    private Long saveInProgressGameRoom() {
        GameRoom gameRoom = GameRoom.builder()
                .durationSeconds(10)
                .scenarioData(GameScenario.of(List.of(new HpStep(0, GameRoom.DEFAULT_STAR_CORE_MAX_HP))))
                .build();
        gameRoom.addParticipant(FIRST_USER_ID);
        gameRoom.addParticipant(SECOND_USER_ID);
        gameRoom.start(LocalDateTime.now());
        return gameRoomRepository.save(gameRoom).getId();
    }

    private TransactionSynchronization simulatedCrash() {
        return new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                throw new SimulatedProcessCrashException();
            }
        };
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private static class SimulatedProcessCrashException extends RuntimeException {
    }
}
