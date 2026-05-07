package com.sang.smite.matching;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class TestMatchingApplication {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
