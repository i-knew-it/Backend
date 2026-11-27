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
