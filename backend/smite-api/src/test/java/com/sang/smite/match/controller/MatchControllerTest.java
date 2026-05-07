package com.sang.smite.match.controller;

import com.sang.smite.match.service.MatchQueueService;
import com.sang.smite.match.service.MatchResponseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MatchControllerTest {

    private final MatchQueueService matchQueueService = mock(MatchQueueService.class);
    private final MatchResponseService matchResponseService = mock(MatchResponseService.class);
    private final MatchController controller = new MatchController(matchQueueService, matchResponseService);

    @Test
    @DisplayName("매칭 수락 요청을 서비스에 위임한다.")
    void accept() {
        ResponseEntity<Void> response = controller.accept("match-1", 1L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(matchResponseService).accept("match-1", 1L);
    }

    @Test
    @DisplayName("매칭 거절 요청을 서비스에 위임한다.")
    void reject() {
        ResponseEntity<Void> response = controller.reject("match-1", 1L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(matchResponseService).reject("match-1", 1L);
    }
}
