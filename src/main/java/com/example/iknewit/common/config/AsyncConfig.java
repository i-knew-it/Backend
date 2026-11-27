package com.example.iknewit.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// 메일 발송처럼 느린 작업을 요청 처리와 분리하기 위해 @Async를 켠다
@Configuration
@EnableAsync
public class AsyncConfig {
}
