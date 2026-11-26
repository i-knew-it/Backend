package com.example.iknewit.auth.social;

import com.example.iknewit.auth.AuthResult;
import com.example.iknewit.auth.RefreshCookieFactory;
import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

// Google 인증이 끝나면 호출된다. 사용자를 찾거나 만들고, Refresh Token 쿠키만 세팅한 뒤 프론트로 리다이렉트한다
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

	private final SocialAuthService socialAuthService;
	private final RefreshCookieFactory cookieFactory;
	private final FrontendRedirector redirector;

	public OAuth2LoginSuccessHandler(SocialAuthService socialAuthService, RefreshCookieFactory cookieFactory,
		FrontendRedirector redirector) {
		this.socialAuthService = socialAuthService;
		this.cookieFactory = cookieFactory;
		this.redirector = redirector;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
		Authentication authentication) throws IOException {
		try {
			OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
			OAuth2UserInfo info = OAuth2UserInfoFactory.create(
				token.getAuthorizedClientRegistrationId(), token.getPrincipal().getAttributes());
			AuthResult result = socialAuthService.loginOrSignup(info);

			response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.create(result.refreshToken()).toString());
			invalidateSession(request);
			redirector.success(response);
		} catch (ApiException e) {
			invalidateSession(request);
			redirector.failure(response, e.getErrorCode());
		} catch (RuntimeException e) {
			log.warn("소셜 로그인 처리 중 오류", e);
			invalidateSession(request);
			redirector.failure(response, ErrorCode.OAUTH_FAILED);
		}
	}

	// 세션은 OAuth 핸드셰이크(state 저장)에만 쓰므로 끝나면 버린다
	private void invalidateSession(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
	}
}
