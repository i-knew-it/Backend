package com.example.iknewit.auth.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "auth.rate-limit")
public record RateLimitProperties(
	@DefaultValue("5") int loginMaxFailures,
	@DefaultValue("15m") Duration loginBlockDuration,
	@DefaultValue("20") int ipMaxPerMinute
) {
}
