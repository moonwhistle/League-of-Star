package com.sang.leagueofstar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class LeagueOfStarApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeagueOfStarApplication.class, args);
	}

}
