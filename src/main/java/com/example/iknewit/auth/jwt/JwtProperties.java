package com.example.iknewit.auth.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// application.yaml의 jwt.* (secret은 환경변수 JWT_SECRET)
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
	String secret,
	Duration accessTokenExpiration,
	Duration refreshTokenExpiration,
	// 방금 교체된 Refresh Token이 이 시간 안에 다시 오면 동시 요청으로 보고 허용
	@DefaultValue("10s") Duration refreshReuseGrace
) {
}
