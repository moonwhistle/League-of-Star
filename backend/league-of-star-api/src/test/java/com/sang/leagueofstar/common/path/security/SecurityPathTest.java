package com.sang.leagueofstar.common.path.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPathTest {

    @Test
    @DisplayName("AUTH_WHITELIST - custom room WebSocket endpoint를 포함한다")
    void authWhitelist_ContainsCustomRoomWebSocketPath() {
        assertThat(SecurityPath.AUTH_WHITELIST)
                .contains("/ws/custom-games/rooms/**");
    }
}
