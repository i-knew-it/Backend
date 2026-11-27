package com.example.iknewit.auth.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "auth.rate-limit")
public record RateLimitProperties(
	@DefaultValue("5") int loginMaxFailures,
	@DefaultValue("15m") Duration loginBlockDuration,
	@DefaultValue("20") int ipMaxPerMinute,
	// 같은 이메일로 비밀번호 재설정 메일을 요청할 수 있는 횟수 (이메일 존재 여부와 무관하게 센다)
	@DefaultValue("3") int passwordResetMaxPerHour,
	@DefaultValue("1h") Duration passwordResetWindow
) {
}
