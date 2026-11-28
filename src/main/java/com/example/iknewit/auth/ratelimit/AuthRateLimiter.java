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
	private static final Duration IP_WINDOW = Duration.ofMinutes(1);

	private record FailureRecord(int count, Instant lastFailureAt) {
	}

	// 고정 윈도우 카운터. 차단 응답의 Retry-After를 "윈도우가 끝날 때까지 남은 시간"으로 계산하려고 시작 시각을 함께 둔다.
	// 횟수를 올리는 것은 캐시 쓰기가 아니라서 조회만으로 윈도우(만료 시각)가 늘어나지 않는다
	private record Window(Instant startedAt, AtomicInteger count) {
	}

	private final RateLimitProperties properties;
	private final Clock clock;
	private final Cache<String, FailureRecord> failures;
	private final Cache<String, Window> ipCounters;
	private final Cache<String, Window> passwordResetCounters;

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
			.expireAfterWrite(IP_WINDOW).maximumSize(MAX_ENTRIES).build();
		// 이메일별 재설정 요청 횟수 (첫 요청 시각부터 고정 윈도우)
		this.passwordResetCounters = Caffeine.newBuilder().ticker(ticker)
			.expireAfterWrite(properties.passwordResetWindow()).maximumSize(MAX_ENTRIES).build();
	}

	public void checkIp(String ip) {
		Window window = ipCounters.get(ip, key -> newWindow());
		if (window.count().incrementAndGet() > properties.ipMaxPerMinute()) {
			throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS.getMessage(),
				retryAfterSeconds(window, IP_WINDOW));
		}
	}

	/**
	 * 같은 이메일로 비밀번호 재설정 메일을 너무 자주 요청하지 못하게 한다 (메일 폭탄 방지).
	 * 이메일이 가입되어 있는지와 무관하게 똑같이 센다. 가입된 이메일에만 제한이 걸리면
	 * 429 여부로 가입 여부를 알아낼 수 있기 때문이다.
	 */
	public void checkPasswordResetRequest(String email) {
		Window window = passwordResetCounters.get(Emails.normalize(email), key -> newWindow());
		if (window.count().incrementAndGet() > properties.passwordResetMaxPerHour()) {
			throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
				"재설정 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.",
				retryAfterSeconds(window, properties.passwordResetWindow()));
		}
	}

	private Window newWindow() {
		return new Window(clock.instant(), new AtomicInteger());
	}

	// 윈도우가 끝날 때까지 남은 시간(초). 올림하고 최소 1초 (0초면 "바로 다시 시도"로 오해하고 또 차단될 수 있음)
	private long retryAfterSeconds(Window window, Duration windowLength) {
		Duration remaining = Duration.between(clock.instant(), window.startedAt().plus(windowLength));
		return Math.max(1, (remaining.toMillis() + 999) / 1000);
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
