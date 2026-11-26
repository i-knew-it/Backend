package com.example.iknewit.auth.social;

import com.example.iknewit.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

// Google 인증 단계에서 실패(사용자가 취소, state 불일치, 토큰 교환 실패 등)하면 프론트 로그인 페이지로 보낸다
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

	private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

	private final FrontendRedirector redirector;

	public OAuth2LoginFailureHandler(FrontendRedirector redirector) {
		this.redirector = redirector;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException exception) throws IOException {
		// 원인(access_denied, authorization_request_not_found 등)은 서버 로그에만 남기고 프론트에는 일반 코드만 전달
		String reason = exception instanceof OAuth2AuthenticationException oauth
			? oauth.getError().getErrorCode() : exception.getClass().getSimpleName();
		log.warn("소셜 로그인 실패: {}", reason);

		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		redirector.failure(response, ErrorCode.OAUTH_FAILED);
	}
}
