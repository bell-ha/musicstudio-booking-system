package com.musicstudio.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 현재 시각은 이 Clock으로만 읽는다. 테스트가 시각을 고정할 수 있게 하려는 것이다. */
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
