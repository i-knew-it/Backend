package com.example.iknewit.auth.social;

import com.example.iknewit.common.config.AppProperties;
import com.example.iknewit.common.exception.ErrorCode;
import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 소셜 로그인 결과를 프론트 페이지로 리다이렉트한다.
 * 성공: {frontend}/auth/callback (토큰은 URL에 싣지 않는다. 프론트가 POST /auth/token/refresh로 Access Token을 받는다)
 * 실패: {frontend}/login?error=코드
 * 리다이렉트 대상은 서버 설정값으로 고정되어 있어 요청 값으로 바뀌지 않는다 (오픈 리다이렉트 방지)
 */
@Component
public class FrontendRedirector {

	private final AppProperties appProperties;

	public FrontendRedirector(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	public void success(HttpServletResponse response) throws IOException {
		response.sendRedirect(UriComponentsBuilder.fromUriString(appProperties.primaryFrontendUrl())
			.path(appProperties.oauth2SuccessPath())
			.build().toUriString());
	}

	public void failure(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.sendRedirect(UriComponentsBuilder.fromUriString(appProperties.primaryFrontendUrl())
			.path(appProperties.oauth2FailurePath())
			.queryParam("error", errorCode.name())
			.build().toUriString());
	}
}
