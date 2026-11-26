package com.example.iknewit.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.iknewit.user.AuthProvider;
import com.example.iknewit.user.User;
import com.example.iknewit.user.UserRepository;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 흐름 중 Google 서버와 통신하지 않는 부분을 검증한다.
 *  - 시작(authorize): Google 로그인 화면으로 보내는 리다이렉트
 *  - 콜백 실패 처리, 성공/실패 핸들러의 프론트 리다이렉트와 쿠키
 * Google과 실제로 주고받는 구간(코드→토큰 교환)은 자동 테스트가 불가능해 브라우저로 직접 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OAuth2LoginFlowTest {

	private static final String FRONTEND = "http://localhost:3000";

	@Autowired
	MockMvc mockMvc;
	@Autowired
	OAuth2LoginSuccessHandler successHandler;
	@Autowired
	OAuth2LoginFailureHandler failureHandler;
	@Autowired
	UserRepository userRepository;

	private OAuth2AuthenticationToken googleToken(String email, Object verified, String name) {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put("sub", "g-" + UUID.randomUUID());
		attributes.put("email", email);
		attributes.put("email_verified", verified);
		attributes.put("name", name);
		DefaultOAuth2User principal = new DefaultOAuth2User(
			List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub");
		return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
	}

	private String uniqueEmail() {
		return "flow-" + UUID.randomUUID() + "@gmail.com";
	}

	@Test
	void 시작_경로는_Google_로그인_화면으로_리다이렉트한다() throws Exception {
		MvcResult result = mockMvc.perform(get("/auth/oauth2/authorize/google")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(302);
		String location = result.getResponse().getRedirectedUrl();
		assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
		assertThat(location).contains("response_type=code", "scope=profile%20email", "state=", "client_id=");
		// Google Cloud Console에 등록한 리디렉션 URI 형태: {baseUrl}/auth/oauth2/callback/google
		assertThat(location).contains("redirect_uri=http://localhost/auth/oauth2/callback/google");
		// 인증은 STATELESS여도, 핸드셰이크의 state는 콜백에서 검증하려고 세션에 저장되어야 한다
		assertThat(result.getRequest().getSession(false)).isNotNull();
		assertThat(Collections.list(result.getRequest().getSession(false).getAttributeNames()))
			.anyMatch(name -> name.contains("AUTHORIZATION_REQUEST"));
	}

	@Test
	void 시작하지_않은_상태로_콜백이_오면_실패로_프론트에_돌려보낸다() throws Exception {
		// state를 저장한 세션이 없으므로 Google에 토큰 요청을 보내지 않고 실패 처리된다
		MvcResult result = mockMvc.perform(get("/auth/oauth2/callback/google")
			.param("code", "fake-code").param("state", "fake-state")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(302);
		assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(FRONTEND + "/login?error=OAUTH_FAILED");
		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
	}

	@Test
	void 성공하면_리프레시_쿠키만_세팅하고_토큰은_URL에_싣지_않고_프론트로_보낸다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		String email = uniqueEmail();

		successHandler.onAuthenticationSuccess(request, response, googleToken(email, true, "홍길동"));

		assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/auth/callback");
		String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith("refreshToken=")
			.contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/auth");
		assertThat(response.getRedirectedUrl()).doesNotContain("token").doesNotContain("?");

		User created = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow();
		assertThat(created.getProvider()).isEqualTo(AuthProvider.GOOGLE);
		assertThat(created.getNickname()).isEqualTo("홍길동");
	}

	@Test
	void 같은_이메일의_일반_계정이_있으면_EMAIL_ALREADY_EXISTS로_프론트에_알린다() throws Exception {
		String email = uniqueEmail();
		userRepository.saveAndFlush(User.builder().email(email).passwordHash("{bcrypt}x").nickname("기존회원")
			.provider(AuthProvider.EMAIL).build());
		MockHttpServletResponse response = new MockHttpServletResponse();

		successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, googleToken(email, true, "tester"));

		assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/login?error=EMAIL_ALREADY_EXISTS");
		assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
	}

	@Test
	void 이메일_미인증_계정은_EMAIL_NOT_VERIFIED로_프론트에_알린다() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
			googleToken(uniqueEmail(), false, "tester"));

		assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/login?error=EMAIL_NOT_VERIFIED");
		assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
	}

	@Test
	void 사용자가_Google_로그인을_취소하면_OAUTH_FAILED로_프론트에_알리고_원인은_노출하지_않는다() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		failureHandler.onAuthenticationFailure(new MockHttpServletRequest(), response,
			new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

		assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/login?error=OAUTH_FAILED");
		assertThat(response.getRedirectedUrl()).doesNotContain("access_denied");
	}
}
