package com.example.iknewit.auth.jwt;

import com.example.iknewit.common.exception.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {token}을 읽어 유효하면 SecurityContext에 인증 정보를 설정한다.
 * 토큰이 없거나 잘못돼도 여기서는 막지 않고 통과시킨다.
 * (인증이 필요한 경로에서 AuthenticationEntryPoint가 401을 응답)
 * 실패 사유(만료/무효)는 request attribute에 남겨 entry point가 에러 코드를 구분하게 한다.
 *
 * @Component로 등록하지 않는다: 서블릿 필터로 중복 등록되는 것을 막기 위해 SecurityConfig에서 직접 생성
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	public static final String ERROR_ATTRIBUTE = "jwt.error";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtProvider jwtProvider;

	public JwtAuthenticationFilter(JwtProvider jwtProvider) {
		this.jwtProvider = jwtProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			String token = header.substring(BEARER_PREFIX.length()).trim();
			try {
				String userId = jwtProvider.parseUserId(token, TokenType.ACCESS);
				// principal은 userId(String)
				SecurityContextHolder.getContext().setAuthentication(
					UsernamePasswordAuthenticationToken.authenticated(userId, null,
						List.of(new SimpleGrantedAuthority("ROLE_USER"))));
			} catch (ApiException e) {
				SecurityContextHolder.clearContext();
				request.setAttribute(ERROR_ATTRIBUTE, e.getErrorCode());
			}
		}
		chain.doFilter(request, response);
	}
}
