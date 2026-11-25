package com.example.iknewit.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	OpenAPI openAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("아는 줄 알았어 API")
				.description("AI 기반 개발자 면접 학습 서비스 백엔드 API")
				.version("v1"))
			// 문서의 경로에 이미 /v1이 포함되므로 서버 URL에는 /v1을 넣지 않음 (WebMvcConfig 참고)
			// "/"는 현재 접속한 주소를 그대로 사용 → 로컬 포트가 바뀌어도 수정 불필요
			.servers(List.of(
				new Server().url("/").description("현재 서버"),
				new Server().url("https://api.anunjul.app").description("운영")))
			// Authorize 버튼에서 Access Token(JWT)을 입력하면 모든 요청에 Bearer 헤더가 붙음
			.components(new Components().addSecuritySchemes(BEARER_SCHEME,
				new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}
}
