package com.example.iknewit.auth.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class AuthRateLimiterTest {

	private static final String IP = "203.0.113.7";
	private static final String EMAIL = "user@example.com";

	// 시간을 직접 움직일 수 있는 테스트용 시계
	static class MutableClock extends Clock {
		private Instant now = Instant.parse("2026-01-01T00:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	private final MutableClock clock = new MutableClock();
	private final AuthRateLimiter limiter = new AuthRateLimiter(
		new RateLimitProperties(5, Duration.ofMinutes(15), 20, 3, Duration.ofHours(1)), clock);

	private void fail(int times) {
		for (int i = 0; i < times; i++) {
			limiter.checkLogin(IP, EMAIL);
			limiter.recordFailure(IP, EMAIL);
		}
	}

	@Test
	void 연속_4번_실패까지는_허용한다() {
		fail(4);

		assertThatCode(() -> limiter.checkLogin(IP, EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void 연속_5번_실패하면_차단하고_남은_시간을_알려준다() {
		fail(5);

		assertThatThrownBy(() -> limiter.checkLogin(IP, EMAIL))
			.isInstanceOfSatisfying(ApiException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
				assertThat(e.getRetryAfterSeconds()).isBetween(1L, 900L);
				assertThat(e.getMessage()).contains("15분");
			});
	}

	@Test
	void 차단_시간이_지나면_다시_시도할_수_있다() {
		fail(5);

		clock.advance(Duration.ofMinutes(15).plusSeconds(1));

		assertThatCode(() -> limiter.checkLogin(IP, EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void 차단된_동안_시도해도_차단_시간이_늘어나지_않는다() {
		fail(5);
		clock.advance(Duration.ofMinutes(10));
		assertThatThrownBy(() -> limiter.checkLogin(IP, EMAIL)).isInstanceOf(ApiException.class);

		clock.advance(Duration.ofMinutes(6)); // 마지막 실패로부터 16분

		assertThatCode(() -> limiter.checkLogin(IP, EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void 로그인에_성공하면_실패_횟수가_초기화된다() {
		fail(4);
		limiter.recordSuccess(IP, EMAIL);
		fail(4);

		assertThatCode(() -> limiter.checkLogin(IP, EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void 이메일_대소문자와_공백은_같은_키로_본다() {
		fail(5);

		assertThatThrownBy(() -> limiter.checkLogin(IP, "  USER@Example.com "))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void 다른_IP나_다른_이메일은_영향을_받지_않는다() {
		fail(5);

		// 남이 내 계정을 일부러 잠그지 못하도록, 다른 IP에서의 같은 이메일은 막지 않는다
		assertThatCode(() -> limiter.checkLogin("198.51.100.9", EMAIL)).doesNotThrowAnyException();
		assertThatCode(() -> limiter.checkLogin(IP, "other@example.com")).doesNotThrowAnyException();
	}

	@Test
	void IP는_분당_20회까지_허용하고_초과하면_429다() {
		for (int i = 0; i < 20; i++) {
			limiter.checkIp(IP);
		}

		assertThatThrownBy(() -> limiter.checkIp(IP))
			.isInstanceOfSatisfying(ApiException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
				assertThat(e.getRetryAfterSeconds()).isEqualTo(60L);
			});
		assertThatCode(() -> limiter.checkIp("198.51.100.9")).doesNotThrowAnyException();
	}

	@Test
	void IP_집계는_1분이_지나면_초기화된다() {
		for (int i = 0; i < 21; i++) {
			try {
				limiter.checkIp(IP);
			} catch (ApiException ignored) {
				// 21번째는 차단되는 것이 정상
			}
		}

		clock.advance(Duration.ofSeconds(61));

		assertThatCode(() -> limiter.checkIp(IP)).doesNotThrowAnyException();
	}

	// ---------- 비밀번호 재설정 요청 제한 (이메일당) ----------

	@Test
	void 같은_이메일의_재설정_요청은_1시간에_3회까지_허용한다() {
		for (int i = 0; i < 3; i++) {
			limiter.checkPasswordResetRequest(EMAIL);
		}

		assertThatThrownBy(() -> limiter.checkPasswordResetRequest(EMAIL))
			.isInstanceOfSatisfying(ApiException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
				assertThat(e.getRetryAfterSeconds()).isEqualTo(3600L);
			});
	}

	@Test
	void 재설정_요청_횟수는_이메일_대소문자와_공백을_무시하고_센다() {
		limiter.checkPasswordResetRequest("user@example.com");
		limiter.checkPasswordResetRequest("USER@example.com");
		limiter.checkPasswordResetRequest("  User@Example.com ");

		assertThatThrownBy(() -> limiter.checkPasswordResetRequest(EMAIL)).isInstanceOf(ApiException.class);
	}

	@Test
	void 다른_이메일의_재설정_요청은_영향을_받지_않는다() {
		for (int i = 0; i < 3; i++) {
			limiter.checkPasswordResetRequest(EMAIL);
		}

		assertThatCode(() -> limiter.checkPasswordResetRequest("other@example.com")).doesNotThrowAnyException();
	}

	// ---------- Retry-After는 "윈도우가 끝날 때까지 남은 시간"이어야 한다 ----------

	private long retryAfterOfBlockedResetRequest() {
		try {
			limiter.checkPasswordResetRequest(EMAIL);
		} catch (ApiException e) {
			return e.getRetryAfterSeconds();
		}
		throw new AssertionError("차단되어야 하는데 통과했다");
	}

	private long retryAfterOfBlockedIp() {
		try {
			limiter.checkIp(IP);
		} catch (ApiException e) {
			return e.getRetryAfterSeconds();
		}
		throw new AssertionError("차단되어야 하는데 통과했다");
	}

	@Test
	void 재설정_요청의_Retry_After는_윈도우_전체가_아니라_남은_시간이다() {
		for (int i = 0; i < 3; i++) {
			limiter.checkPasswordResetRequest(EMAIL);
		}

		// 한도를 넘긴 직후: 윈도우가 막 시작된 상태라 1시간
		assertThat(retryAfterOfBlockedResetRequest()).isEqualTo(3600L);

		// 30분 뒤: 남은 시간은 30분
		clock.advance(Duration.ofMinutes(30));
		assertThat(retryAfterOfBlockedResetRequest()).isEqualTo(1800L);

		// 만료 1초 전: 1초
		clock.advance(Duration.ofMinutes(29).plusSeconds(59));
		assertThat(retryAfterOfBlockedResetRequest()).isEqualTo(1L);
	}

	@Test
	void Retry_After는_초_단위로_올림하고_최소_1초다() {
		for (int i = 0; i < 3; i++) {
			limiter.checkPasswordResetRequest(EMAIL);
		}

		// 남은 시간 1.2초 → 올림해서 2초
		clock.advance(Duration.ofHours(1).minusMillis(1200));
		assertThat(retryAfterOfBlockedResetRequest()).isEqualTo(2L);

		// 남은 시간 0.5초 → 올림해서 1초 (0초로 내려가면 바로 다시 시도해서 또 차단된다)
		clock.advance(Duration.ofMillis(700));
		assertThat(retryAfterOfBlockedResetRequest()).isEqualTo(1L);
	}

	@Test
	void Retry_After_올림은_1밀리초_미만의_잔여분도_반영한다() {
		for (int i = 0; i < 3; i++) {
			limiter.checkPasswordResetRequest(EMAIL);
		}

		// 남은 시간 1.2초 → 2초
		clock.advance(Duration.ofHours(1).minusMillis(1200));
		assertThat(retryAfterOfBlockedResetRequest()).isEqualTo(2L);

		// 남은 시간 1.0005초(1초 + 500마이크로초) → 밀리초로 자르면 1초가 되지만 올림하면 2초
		clock.advance(Duration.ofMillis(199).plusNanos(500_000));
		assertThat(retryAfterOfBlockedResetRequest()).isEqualTo(2L);

		// 남은 시간 정확히 1초 → 1초
		clock.advance(Duration.ofNanos(500_000));
		assertThat(retryAfterOfBlockedResetRequest()).isEqualTo(1L);

		// 남은 시간 1나노초 → 최소 1초
		clock.advance(Duration.ofSeconds(1).minusNanos(1));
		assertThat(retryAfterOfBlockedResetRequest()).isEqualTo(1L);
	}

	@Test
	void 로그인_차단의_Retry_After도_1밀리초_미만의_잔여분을_올림한다() {
		fail(5);

		// 차단 시간 15분 중 남은 시간이 1.0005초가 되는 시점
		clock.advance(Duration.ofMinutes(15).minusMillis(1000).minusNanos(500_000));
		assertThatThrownBy(() -> limiter.checkLogin(IP, EMAIL))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(2L));
	}

	@Test
	void 차단된_요청을_계속_보내도_윈도우가_늘어나지_않는다() {
		for (int i = 0; i < 3; i++) {
			limiter.checkPasswordResetRequest(EMAIL);
		}

		clock.advance(Duration.ofMinutes(10));
		long first = retryAfterOfBlockedResetRequest();
		clock.advance(Duration.ofMinutes(10));
		long second = retryAfterOfBlockedResetRequest();

		// 차단되어 조회만 했을 뿐이므로 만료 시각은 처음 시작 시각 기준 그대로 줄어든다
		assertThat(first).isEqualTo(3000L);
		assertThat(second).isEqualTo(2400L);
	}

	@Test
	void IP_제한의_Retry_After도_남은_시간이다() {
		for (int i = 0; i < 20; i++) {
			limiter.checkIp(IP);
		}

		assertThat(retryAfterOfBlockedIp()).isEqualTo(60L);

		clock.advance(Duration.ofSeconds(25));
		assertThat(retryAfterOfBlockedIp()).isEqualTo(35L);

		clock.advance(Duration.ofSeconds(34));
		assertThat(retryAfterOfBlockedIp()).isEqualTo(1L);
	}

	@Test
	void 재설정_요청_횟수는_1시간이_지나면_초기화된다() {
		for (int i = 0; i < 4; i++) {
			try {
				limiter.checkPasswordResetRequest(EMAIL);
			} catch (ApiException ignored) {
				// 4번째는 차단되는 것이 정상
			}
		}

		clock.advance(Duration.ofHours(1).plusSeconds(1));

		assertThatCode(() -> limiter.checkPasswordResetRequest(EMAIL)).doesNotThrowAnyException();
	}
}
