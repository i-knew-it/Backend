package com.example.iknewit.auth;

import com.example.iknewit.auth.jwt.JwtProvider;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// HttpOnly(JS 접근 차단), Secure(HTTPS만), SameSite=Strict(CSRF 완화), Path=/auth(auth 요청에만 전송)
@Component
public class RefreshCookieFactory {

	public static final String NAME = "refreshToken";
	private static final String PATH = "/auth";

	private final JwtProvider jwtProvider;

	public RefreshCookieFactory(JwtProvider jwtProvider) {
		this.jwtProvider = jwtProvider;
	}

	public ResponseCookie create(String refreshToken) {
		return build(refreshToken, jwtProvider.refreshTokenExpiration());
	}

	// 브라우저가 쓸모없어진 쿠키를 계속 보내지 않도록 만료시킨다 (Max-Age=0)
	public ResponseCookie expire() {
		return build("", Duration.ZERO);
	}

	private ResponseCookie build(String value, Duration maxAge) {
		return ResponseCookie.from(NAME, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Strict")
			.path(PATH)
			.maxAge(maxAge)
			.build();
	}
}
