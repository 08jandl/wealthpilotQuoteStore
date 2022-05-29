package com.wealthpilot.quote.store;

import org.ff4j.FF4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class FF4jTestConfig {

    @Bean
    @Profile("test")
    public FF4j getFF4j() {
        return new FF4j();
    }
}
