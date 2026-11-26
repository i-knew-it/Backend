package com.example.iknewit.auth.ratelimit;

import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import com.example.iknewit.user.Emails;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 무차별 대입(brute force) 방어. 서버 메모리 기반이라 재시작하면 초기화되고, 서버가 여러 대면 공유되지 않는다.
 * (EC2 한 대 MVP 기준. 확장 시 Redis 등으로 교체)
 *
 * 1) 로그인 실패 제한: 같은 "이메일 + IP"에서 N회 연속 실패하면 일정 시간 차단.
 *    이메일만으로 차단하면 남의 계정을 일부러 잠그는 공격이 가능해서 IP를 키에 포함한다.
 * 2) IP 제한: 같은 IP에서 로그인/가입 시도를 분당 N회로 제한 (여러 이메일을 번갈아 시도하는 공격 대응)
 */
@Component
public class AuthRateLimiter {

	private static final long MAX_ENTRIES = 100_000; // 메모리 고갈 방지
	private static final long IP_WINDOW_RETRY_SECONDS = 60;

	private record FailureRecord(int count, Instant lastFailureAt) {
	}

	private final RateLimitProperties properties;
	private final Clock clock;
	private final Cache<String, FailureRecord> failures;
	private final Cache<String, AtomicInteger> ipCounters;

	@Autowired
	public AuthRateLimiter(RateLimitProperties properties) {
		this(properties, Clock.systemUTC());
	}

	// 테스트에서 시간을 제어하기 위한 생성자
	public AuthRateLimiter(RateLimitProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		Ticker ticker = () -> TimeUnit.MILLISECONDS.toNanos(clock.millis());
		// 마지막 실패 기준으로 차단 시간이 지나면 기록이 사라진다
		this.failures = Caffeine.newBuilder().ticker(ticker)
			.expireAfterWrite(properties.loginBlockDuration()).maximumSize(MAX_ENTRIES).build();
		// 첫 요청 시각부터 1분간 집계하는 고정 윈도우
		this.ipCounters = Caffeine.newBuilder().ticker(ticker)
			.expireAfterWrite(Duration.ofMinutes(1)).maximumSize(MAX_ENTRIES).build();
	}

	public void checkIp(String ip) {
		int count = ipCounters.get(ip, key -> new AtomicInteger()).incrementAndGet();
		if (count > properties.ipMaxPerMinute()) {
			throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS.getMessage(),
				IP_WINDOW_RETRY_SECONDS);
		}
	}

	public void checkLogin(String ip, String email) {
		FailureRecord record = failures.getIfPresent(key(ip, email));
		if (record != null && record.count() >= properties.loginMaxFailures()) {
			Duration remaining = Duration.between(clock.instant(), record.lastFailureAt().plus(properties.loginBlockDuration()));
			long seconds = Math.max(1, (remaining.toMillis() + 999) / 1000);
			long minutes = (seconds + 59) / 60;
			throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
				"로그인 시도가 너무 많습니다. " + minutes + "분 후 다시 시도해주세요.", seconds);
		}
	}

	// 차단된 상태의 요청은 checkLogin에서 막히므로 여기까지 오지 않는다 (차단 시간이 계속 연장되지 않음)
	public void recordFailure(String ip, String email) {
		failures.asMap().compute(key(ip, email), (key, current) ->
			new FailureRecord(current == null ? 1 : current.count() + 1, clock.instant()));
	}

	public void recordSuccess(String ip, String email) {
		failures.invalidate(key(ip, email));
	}

	private String key(String ip, String email) {
		return Emails.normalize(email) + "|" + ip;
	}
}
