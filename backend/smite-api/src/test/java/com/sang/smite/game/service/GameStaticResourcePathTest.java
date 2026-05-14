package com.sang.smite.game.service;

import com.sang.smite.redis.AbstractRedisTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class GameStaticResourcePathTest extends AbstractRedisTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("static game asset 경로가 classpath resource에 포함된다")
    void staticGameAssetPathExists() {
        ClassPathResource resource = new ClassPathResource("static/assets/game/.gitkeep");

        assertThat(resource.exists()).isTrue();
    }

    @Test
    @DisplayName("static game asset 경로는 인증 없이 HTTP로 접근 가능하다")
    void staticGameAssetPathServedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/assets/game/.gitkeep"))
                .andExpect(status().isOk());
    }
}
