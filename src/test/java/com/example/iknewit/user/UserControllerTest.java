package com.example.iknewit.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.iknewit.auth.AuthResult;
import com.example.iknewit.auth.jwt.JwtProvider;
import com.example.iknewit.auth.social.GoogleOAuth2UserInfo;
import com.example.iknewit.auth.social.SocialAuthService;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

// 로컬 DB가 떠 있어야 하고 local 프로필로 실행. 트랜잭션 롤백이라 DB에 데이터가 남지 않는다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	UserRepository userRepository;
	@Autowired
	JwtProvider jwtProvider;
	@Autowired
	SocialAuthService socialAuthService;

	private User saveEmailUser(String nickname) {
		return userRepository.saveAndFlush(User.builder()
			.email("me-" + UUID.randomUUID() + "@example.com")
			.passwordHash("{bcrypt}x")
			.nickname(nickname)
			.provider(AuthProvider.EMAIL)
			.build());
	}

	private String bearer(User user) {
		return "Bearer " + jwtProvider.createAccessToken(user.getId());
	}

	@Test
	void 이메일_가입_사용자의_내_정보를_조회한다() throws Exception {
		User user = saveEmailUser("홍길동");

		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(user.getId()))
			.andExpect(jsonPath("$.email").value(user.getEmail()))
			.andExpect(jsonPath("$.nickname").value("홍길동"))
			.andExpect(jsonPath("$.provider").value("EMAIL"))
			.andExpect(jsonPath("$.onboardingCompleted").value(false))
			.andExpect(jsonPath("$.timezone").value("Asia/Seoul"));
	}

	@Test
	void 내부_값은_응답에_노출하지_않는다() throws Exception {
		User user = saveEmailUser("홍길동");

		MvcResult result = mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
			.andExpect(status().isOk()).andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain("password", "bcrypt", "providerId", "deletedAt", "createdAt");
	}

	@Test
	void Google로_가입한_사용자도_조회할_수_있다() throws Exception {
		String email = "google-" + UUID.randomUUID() + "@gmail.com";
		AuthResult login = socialAuthService.loginOrSignup(new GoogleOAuth2UserInfo(Map.of(
			"sub", "g-" + UUID.randomUUID(), "email", email, "email_verified", true, "name", "홍 지호")));

		MvcResult result = mockMvc.perform(get("/users/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
			.andExpect(status().isOk()).andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat((String) JsonPath.read(body, "$.provider")).isEqualTo("GOOGLE");
		assertThat((String) JsonPath.read(body, "$.email")).isEqualTo(email);
		assertThat((String) JsonPath.read(body, "$.nickname")).isEqualTo("홍지호");
		assertThat((Boolean) JsonPath.read(body, "$.onboardingCompleted")).isFalse();
	}

	@Test
	void 토큰의_주인_정보만_조회된다() throws Exception {
		User first = saveEmailUser("첫번째");
		User second = saveEmailUser("두번째");

		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, bearer(first)))
			.andExpect(jsonPath("$.nickname").value("첫번째"));
		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, bearer(second)))
			.andExpect(jsonPath("$.nickname").value("두번째"));
	}

	@Test
	void 토큰이_없으면_UNAUTHORIZED다() throws Exception {
		mockMvc.perform(get("/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void 잘못된_토큰은_TOKEN_INVALID다() throws Exception {
		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
	}

	@Test
	void 리프레시_토큰으로는_조회할_수_없다() throws Exception {
		User user = saveEmailUser("홍길동");
		String refreshToken = jwtProvider.createRefreshToken(user.getId()).token();

		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
	}

	@Test
	void 탈퇴한_계정의_토큰은_유효해도_TOKEN_INVALID다() throws Exception {
		User user = saveEmailUser("탈퇴예정");
		String token = bearer(user);
		user.softDelete();
		userRepository.saveAndFlush(user);

		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
	}
}
