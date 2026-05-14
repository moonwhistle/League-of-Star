package com.sang.smite.game.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class GameStaticResourcePathTest {

    @Test
    @DisplayName("static game asset 경로가 classpath resource에 포함된다")
    void staticGameAssetPathExists() {
        ClassPathResource resource = new ClassPathResource("static/assets/game/.gitkeep");

        assertThat(resource.exists()).isTrue();
    }
}
