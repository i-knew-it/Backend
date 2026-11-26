package com.example.iknewit.auth.social;

import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import java.util.Locale;
import java.util.Map;

public final class OAuth2UserInfoFactory {

	private OAuth2UserInfoFactory() {
	}

	// 새 제공자(카카오 등)는 여기에 분기를 추가한다
	public static OAuth2UserInfo create(String registrationId, Map<String, Object> attributes) {
		return switch (registrationId.toLowerCase(Locale.ROOT)) {
			case "google" -> new GoogleOAuth2UserInfo(attributes);
			default -> throw new ApiException(ErrorCode.OAUTH_FAILED);
		};
	}
}
