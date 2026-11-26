package com.example.iknewit.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

	private static final String SECRET = "unit-test-secret-key-must-be-at-least-32-bytes!!";
	private static final String USER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";
	private static final Duration GRACE = Duration.ofSeconds(10);

	private JwtProvider provider(String secret, Duration access, Duration refresh) {
		return new JwtProvider(new JwtProperties(secret, access, refresh, GRACE));
	}

	private JwtProvider provider(Duration access, Duration refresh) {
		return provider(SECRET, access, refresh);
	}

	private JwtProvider defaultProvider() {
		return provider(Duration.ofMinutes(30), Duration.ofDays(7));
	}

	private void assertErrorCode(Runnable action, ErrorCode expected) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}

	@Test
	void 액세스_토큰에서_userId를_꺼낸다() {
		JwtProvider provider = defaultProvider();
		String token = provider.createAccessToken(USER_ID);

		assertThat(provider.parseUserId(token, TokenType.ACCESS)).isEqualTo(USER_ID);
	}

	@Test
	void 리프레시_토큰에서_userId를_꺼내고_만료시각은_7일_뒤다() {
		JwtProvider provider = defaultProvider();
		IssuedToken issued = provider.createRefreshToken(USER_ID);

		assertThat(provider.parseUserId(issued.token(), TokenType.REFRESH)).isEqualTo(USER_ID);
		assertThat(Duration.between(Instant.now(), issued.expiresAt()))
			.isBetween(Duration.ofDays(7).minusMinutes(1), Duration.ofDays(7));
	}

	@Test
	void 같은_사용자라도_발급할_때마다_토큰이_다르다() {
		JwtProvider provider = defaultProvider();

		assertThat(provider.createRefreshToken(USER_ID).token())
			.isNotEqualTo(provider.createRefreshToken(USER_ID).token());
	}

	@Test
	void 리프레시_토큰을_액세스_토큰으로_쓰면_무효다() {
		JwtProvider provider = defaultProvider();
		String refresh = provider.createRefreshToken(USER_ID).token();

		assertErrorCode(() -> provider.parseUserId(refresh, TokenType.ACCESS), ErrorCode.TOKEN_INVALID);
	}

	@Test
	void 만료된_토큰은_TOKEN_EXPIRED다() {
		JwtProvider provider = provider(Duration.ofSeconds(-60), Duration.ofSeconds(-60));
		String token = provider.createAccessToken(USER_ID);

		assertErrorCode(() -> provider.parseUserId(token, TokenType.ACCESS), ErrorCode.TOKEN_EXPIRED);
	}

	@Test
	void 위조되거나_형식이_잘못된_토큰은_TOKEN_INVALID다() {
		JwtProvider provider = defaultProvider();
		String token = provider.createAccessToken(USER_ID);
		String tampered = token.substring(0, token.length() - 2) + (token.endsWith("aa") ? "bb" : "aa");

		assertErrorCode(() -> provider.parseUserId(tampered, TokenType.ACCESS), ErrorCode.TOKEN_INVALID);
		assertErrorCode(() -> provider.parseUserId("not-a-jwt", TokenType.ACCESS), ErrorCode.TOKEN_INVALID);
	}

	@Test
	void 다른_비밀키로_서명한_토큰은_무효다() {
		JwtProvider other = provider("another-secret-key-also-at-least-32-bytes-long!!",
			Duration.ofMinutes(30), Duration.ofDays(7));
		String token = other.createAccessToken(USER_ID);

		assertErrorCode(() -> defaultProvider().parseUserId(token, TokenType.ACCESS), ErrorCode.TOKEN_INVALID);
	}

	@Test
	void 비밀키가_없거나_짧으면_기동에_실패한다() {
		Duration access = Duration.ofMinutes(30);
		Duration refresh = Duration.ofDays(7);

		assertThatThrownBy(() -> provider("", access, refresh)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> provider("short", access, refresh)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> provider(null, access, refresh)).isInstanceOf(IllegalStateException.class);
	}
}
