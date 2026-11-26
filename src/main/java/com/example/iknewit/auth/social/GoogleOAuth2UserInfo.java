package com.example.iknewit.auth.social;

import com.example.iknewit.user.AuthProvider;
import java.util.Map;

// Google userinfo(v3) 응답: sub, email, email_verified, name ...
public record GoogleOAuth2UserInfo(Map<String, Object> attributes) implements OAuth2UserInfo {

	@Override
	public AuthProvider provider() {
		return AuthProvider.GOOGLE;
	}

	@Override
	public String providerId() {
		return stringOrNull("sub");
	}

	@Override
	public String email() {
		return stringOrNull("email");
	}

	@Override
	public boolean emailVerified() {
		// 응답이 boolean 또는 "true" 문자열일 수 있다
		Object value = attributes.get("email_verified");
		return value instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(value));
	}

	@Override
	public String displayName() {
		return stringOrNull("name");
	}

	private String stringOrNull(String key) {
		Object value = attributes.get(key);
		return value == null ? null : value.toString();
	}
}
