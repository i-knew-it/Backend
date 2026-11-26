package com.example.iknewit.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.iknewit.auth.AuthResult;
import com.example.iknewit.auth.AuthService;
import com.example.iknewit.auth.dto.LoginRequest;
import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import com.example.iknewit.user.AuthProvider;
import com.example.iknewit.user.User;
import com.example.iknewit.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// 실제 Google 호출 없이, Google이 돌려준 사용자 정보(가짜)로 가입/로그인 규칙을 검증한다
@SpringBootTest
@Transactional
class SocialAuthServiceTest {

	@Autowired
	SocialAuthService socialAuthService;
	@Autowired
	AuthService authService;
	@Autowired
	UserRepository userRepository;
	@PersistenceContext
	EntityManager em;

	private Map<String, Object> google(String sub, String email, Object verified, String name) {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put("sub", sub);
		attributes.put("email", email);
		attributes.put("email_verified", verified);
		attributes.put("name", name);
		return attributes;
	}

	private OAuth2UserInfo info(Map<String, Object> attributes) {
		return OAuth2UserInfoFactory.create("google", attributes);
	}

	private String uniqueEmail() {
		return "social-" + UUID.randomUUID() + "@gmail.com";
	}

	private long refreshTokenRowsOf(String userId) {
		return (Long) em.createQuery("select count(r) from RefreshToken r where r.userId = :userId")
			.setParameter("userId", userId).getSingleResult();
	}

	@Test
	void 처음_로그인하면_Google_계정으로_가입된다() {
		String email = uniqueEmail();
		String sub = "g-" + UUID.randomUUID();

		AuthResult result = socialAuthService.loginOrSignup(info(google(sub, email, true, "홍 길동!")));

		User user = userRepository.findById(result.user().id()).orElseThrow();
		assertThat(user.getProvider()).isEqualTo(AuthProvider.GOOGLE);
		assertThat(user.getProviderId()).isEqualTo(sub);
		assertThat(user.getEmail()).isEqualTo(email);
		assertThat(user.getNickname()).isEqualTo("홍길동"); // 닉네임 규칙에 맞게 정리
		assertThat(user.getPasswordHash()).isNull();
		assertThat(user.isOnboardingCompleted()).isFalse();
		assertThat(result.accessToken()).isNotBlank();
		assertThat(result.refreshToken()).isNotBlank();
	}

	@Test
	void Google_이메일은_소문자로_정규화해_저장한다() {
		String sub = "g-" + UUID.randomUUID();
		String email = uniqueEmail();

		AuthResult result = socialAuthService.loginOrSignup(info(google(sub, email.toUpperCase(), true, "tester")));

		assertThat(result.user().email()).isEqualTo(email);
	}

	@Test
	void 다시_로그인하면_같은_계정으로_들어가고_새로_가입되지_않는다() {
		String sub = "g-" + UUID.randomUUID();
		String email = uniqueEmail();
		AuthResult first = socialAuthService.loginOrSignup(info(google(sub, email, true, "tester")));
		long usersBefore = userRepository.count();

		AuthResult second = socialAuthService.loginOrSignup(info(google(sub, email, true, "tester")));

		assertThat(second.user().id()).isEqualTo(first.user().id());
		assertThat(userRepository.count()).isEqualTo(usersBefore);
		// 단일 디바이스: 새 로그인 때 이전 토큰이 정리되어 1개만 남는다
		assertThat(refreshTokenRowsOf(first.user().id())).isEqualTo(1);
	}

	@Test
	void 새로_로그인하면_이전_로그인의_리프레시_토큰은_무효가_된다() {
		String sub = "g-" + UUID.randomUUID();
		String email = uniqueEmail();
		AuthResult first = socialAuthService.loginOrSignup(info(google(sub, email, true, "tester")));
		socialAuthService.loginOrSignup(info(google(sub, email, true, "tester")));

		assertThatThrownBy(() -> authService.refresh(first.refreshToken()))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));
	}

	@Test
	void 같은_이메일의_일반_계정이_있으면_자동_연동하지_않고_거절한다() {
		String email = uniqueEmail();
		userRepository.saveAndFlush(User.builder().email(email).passwordHash("{bcrypt}x").nickname("기존회원")
			.provider(AuthProvider.EMAIL).build());
		long usersBefore = userRepository.count();

		assertThatThrownBy(() -> socialAuthService.loginOrSignup(
			info(google("g-" + UUID.randomUUID(), email, true, "tester"))))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
		assertThat(userRepository.count()).isEqualTo(usersBefore);
	}

	@Test
	void 이메일이_인증되지_않은_Google_계정은_거절한다() {
		assertThatThrownBy(() -> socialAuthService.loginOrSignup(
			info(google("g-" + UUID.randomUUID(), uniqueEmail(), false, "tester"))))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));

		// email_verified 값이 아예 없는 경우도 인증되지 않은 것으로 본다
		assertThatThrownBy(() -> socialAuthService.loginOrSignup(
			info(google("g-" + UUID.randomUUID(), uniqueEmail(), null, "tester"))))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
	}

	@Test
	void 이메일이나_사용자_ID가_없으면_거절한다() {
		assertThatThrownBy(() -> socialAuthService.loginOrSignup(info(google("g-1", null, true, "tester"))))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
		assertThatThrownBy(() -> socialAuthService.loginOrSignup(info(google(null, uniqueEmail(), true, "tester"))))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OAUTH_FAILED));
	}

	@Test
	void 소셜로_가입한_계정은_이메일_비밀번호로_로그인할_수_없다() {
		String email = uniqueEmail();
		socialAuthService.loginOrSignup(info(google("g-" + UUID.randomUUID(), email, true, "tester")));

		assertThatThrownBy(() -> authService.login(new LoginRequest(email, "Password123!"), "10.2.0.1"))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
	}
}
