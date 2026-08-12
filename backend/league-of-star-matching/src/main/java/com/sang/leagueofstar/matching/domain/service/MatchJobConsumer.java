package com.sang.leagueofstar.matching.domain.service;

import com.sang.leagueofstar.domain.match.domain.MatchClaim;
import com.sang.leagueofstar.matching.common.constant.MatchingConstants;
import com.sang.leagueofstar.matching.repository.MatchJobStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * XREADGROUP BLOCK으로 신규 MatchJob을 처리하는 인스턴스별 전용 worker입니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "matching.stream.consumer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MatchJobConsumer implements SmartLifecycle {

    private final MatchJobStore matchJobStore;
    private final MatchJobProcessor matchJobProcessor;
    private final MatchConsumerIdentity consumerIdentity;
    private final AtomicBoolean running = new AtomicBoolean();
    private ExecutorService executor;

    public MatchJobConsumer(
            MatchJobStore matchJobStore,
            MatchJobProcessor matchJobProcessor,
            MatchConsumerIdentity consumerIdentity
    ) {
        this.matchJobStore = matchJobStore;
        this.matchJobProcessor = matchJobProcessor;
        this.consumerIdentity = consumerIdentity;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        matchJobStore.initializeConsumerGroup();
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "match-job-consumer");
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(this::consumeLoop);
    }

    @Override
    public void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    private void consumeLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                List<MatchClaim> claims = matchJobStore.readNew(
                        consumerIdentity.consumerName(),
                        MatchingConstants.MATCH_JOB_CONSUMER_BATCH_SIZE,
                        Duration.ofMillis(MatchingConstants.MATCH_JOB_CONSUMER_BLOCK_MILLIS)
                );
                if (!claims.isEmpty()) {
                    matchJobProcessor.process(claims);
                }
            } catch (RuntimeException e) {
                if (running.get()) {
                    log.error("Failed to consume MatchJob stream", e);
                }
            }
        }
    }
}
