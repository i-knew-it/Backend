package com.example.iknewit.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

// 임시 설정: JWT 인증 필터 구현 전까지 전체 허용 (auth 구현 시 교체)
@Configuration
public class SecurityConfig {

	// 인증 적용 후에도 열어 둘 경로 (Swagger UI / OpenAPI 문서)
	private static final String[] SWAGGER_PATHS = {
		"/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
	};

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(SWAGGER_PATHS).permitAll()
				// TODO: auth 구현 시 authenticated()로 변경
				.anyRequest().permitAll())
			.httpBasic(Customizer.withDefaults());
		return http.build();
	}
}
