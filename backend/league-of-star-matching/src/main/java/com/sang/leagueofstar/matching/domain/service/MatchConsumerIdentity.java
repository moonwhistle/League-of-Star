package com.sang.leagueofstar.matching.domain.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumer Group 내에서 애플리케이션 인스턴스를 구분하는 이름을 제공합니다.
 */
@Component
public class MatchConsumerIdentity {

    private final String consumerName;

    public MatchConsumerIdentity(@Value("${matching.stream.consumer-name:}") String configuredName) {
        consumerName = configuredName == null || configuredName.isBlank()
                ? "match-consumer-" + UUID.randomUUID()
                : configuredName;
    }

    public String consumerName() {
        return consumerName;
    }

    public String recoveryConsumerName() {
        return consumerName + "-recovery";
    }
}
