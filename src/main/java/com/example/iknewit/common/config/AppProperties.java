package com.example.iknewit.common.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
	// 쉼표로 여러 origin 가능 (CORS 허용 목록)
	String frontendUrl,
	@DefaultValue("/auth/callback") String oauth2SuccessPath,
	@DefaultValue("/login") String oauth2FailurePath
) {

	// 끝의 슬래시는 CORS 정확 일치 비교와 URL 조합을 깨뜨리므로 제거
	public List<String> frontendOrigins() {
		return Arrays.stream(frontendUrl.split(","))
			.map(String::trim)
			.filter(origin -> !origin.isEmpty())
			.map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
			.toList();
	}

	// 소셜 로그인 후 리다이렉트할 기준 주소 (첫 번째 origin)
	public String primaryFrontendUrl() {
		return frontendOrigins().get(0);
	}
}
