package com.example.iknewit.common.config;

import com.example.iknewit.auth.jwt.JwtAuthenticationFilter;
import com.example.iknewit.auth.jwt.JwtProvider;
import com.example.iknewit.auth.social.OAuth2LoginFailureHandler;
import com.example.iknewit.auth.social.OAuth2LoginSuccessHandler;
import com.example.iknewit.common.exception.ErrorCode;
import com.example.iknewit.common.exception.ErrorResponseWriter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	// 인증 없이 접근 가능한 경로. /error는 에러 디스패치가 401로 가려지지 않게 열어 둔다
	// /auth/** 에는 소셜 로그인 시작(/auth/oauth2/authorize/*)과 콜백(/auth/oauth2/callback/*)도 포함된다
	private static final String[] PUBLIC_PATHS = {
		"/auth/**", "/health", "/error",
		"/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
	};

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, JwtProvider jwtProvider,
		OAuth2LoginSuccessHandler oauth2SuccessHandler, OAuth2LoginFailureHandler oauth2FailureHandler)
		throws Exception {
		http
			// JWT(Authorization 헤더) 기반 API라 CSRF 토큰/기본 로그인 폼은 사용하지 않음
			// 쿠키를 쓰는 /auth/token/refresh는 SameSite=Strict 쿠키로 CSRF를 막고, 소셜 로그인은 state 파라미터로 막는다
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			// 인증은 세션을 쓰지 않는다(JWT). IF_REQUIRED로 두면 SessionManagementFilter가 JWT 필터가 세운 인증 정보를
			// 세션에 저장해서 인증된 요청마다 세션(JSESSIONID)이 생기므로 STATELESS를 쓴다.
			// 소셜 로그인의 state 저장은 Spring이 request.getSession()으로 직접 세션을 만들어 STATELESS여도 동작한다
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PUBLIC_PATHS).permitAll()
				.anyRequest().authenticated())
			.oauth2Login(oauth -> oauth
				.authorizationEndpoint(endpoint -> endpoint.baseUri("/auth/oauth2/authorize"))
				.redirectionEndpoint(endpoint -> endpoint.baseUri("/auth/oauth2/callback/*"))
				.successHandler(oauth2SuccessHandler)
				.failureHandler(oauth2FailureHandler))
			.exceptionHandling(handling -> handling
				// 필터가 남긴 사유(TOKEN_EXPIRED / TOKEN_INVALID)가 없으면 토큰 자체가 없는 것(UNAUTHORIZED)
				.authenticationEntryPoint((request, response, e) -> {
					ErrorCode code = request.getAttribute(JwtAuthenticationFilter.ERROR_ATTRIBUTE) instanceof ErrorCode c
						? c : ErrorCode.UNAUTHORIZED;
					ErrorResponseWriter.write(response, code);
				})
				.accessDeniedHandler((request, response, e) ->
					ErrorResponseWriter.write(response, ErrorCode.FORBIDDEN)))
			.addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	// Refresh Token 쿠키를 주고받으려면 allowCredentials=true와 정확한 origin 지정이 필수 ("*" 불가)
	@Bean
	CorsConfigurationSource corsConfigurationSource(AppProperties appProperties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(appProperties.frontendOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		// 프론트(JS)가 429의 Retry-After를 읽을 수 있도록 노출
		config.setExposedHeaders(List.of("Retry-After"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
