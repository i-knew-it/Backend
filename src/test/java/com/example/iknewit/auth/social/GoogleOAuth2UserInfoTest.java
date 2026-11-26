package com.example.iknewit.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import com.example.iknewit.user.AuthProvider;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoogleOAuth2UserInfoTest {

	@Test
	void Google_userinfo_응답을_공통_형태로_변환한다() {
		OAuth2UserInfo info = OAuth2UserInfoFactory.create("google", Map.of(
			"sub", "1234567890", "email", "user@gmail.com", "email_verified", true, "name", "홍길동"));

		assertThat(info.provider()).isEqualTo(AuthProvider.GOOGLE);
		assertThat(info.providerId()).isEqualTo("1234567890");
		assertThat(info.email()).isEqualTo("user@gmail.com");
		assertThat(info.emailVerified()).isTrue();
		assertThat(info.displayName()).isEqualTo("홍길동");
	}

	@Test
	void email_verified는_boolean_문자열_누락_모두_처리한다() {
		assertThat(new GoogleOAuth2UserInfo(Map.of("email_verified", true)).emailVerified()).isTrue();
		assertThat(new GoogleOAuth2UserInfo(Map.of("email_verified", "true")).emailVerified()).isTrue();
		assertThat(new GoogleOAuth2UserInfo(Map.of("email_verified", false)).emailVerified()).isFalse();
		assertThat(new GoogleOAuth2UserInfo(Map.of("email_verified", "false")).emailVerified()).isFalse();
		// 값이 없으면 인증되지 않은 것으로 본다
		assertThat(new GoogleOAuth2UserInfo(new HashMap<>()).emailVerified()).isFalse();
	}

	@Test
	void 지원하지_않는_제공자는_OAUTH_FAILED다() {
		assertThatThrownBy(() -> OAuth2UserInfoFactory.create("facebook", Map.of()))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OAUTH_FAILED));
	}
}
