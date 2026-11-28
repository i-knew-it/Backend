package com.example.iknewit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.iknewit.auth.dto.LoginRequest;
import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import com.example.iknewit.user.AuthProvider;
import com.example.iknewit.user.User;
import com.example.iknewit.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

// 로컬 DB가 떠 있어야 하고 local 프로필로 실행. 트랜잭션 롤백이라 DB에 데이터가 남지 않는다.
// 테스트 트랜잭션은 커밋되지 않으므로 메일 리스너(AFTER_COMMIT)는 실행되지 않고 실제 메일도 나가지 않는다.
// 토큰 원문은 발행된 이벤트(@RecordApplicationEvents)에서 꺼내 확인한다.
@SpringBootTest(properties = "auth.rate-limit.ip-max-per-minute=100000")
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class PasswordResetControllerTest {

	private static final String OLD_PASSWORD = "OldPassword1!";
	private static final String NEW_PASSWORD = "NewPassword2@";

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ApplicationEvents events;
	@Autowired
	UserRepository userRepository;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	AuthService authService;
	@Autowired
	PasswordResetTokenRepository tokenRepository;
	@PersistenceContext
	EntityManager em;

	private String uniqueEmail() {
		return "reset-" + UUID.randomUUID() + "@example.com";
	}

	private User createEmailUser(String email) {
		return userRepository.saveAndFlush(User.builder().email(email)
			.passwordHash(passwordEncoder.encode(OLD_PASSWORD)).nickname("테스터")
			.provider(AuthProvider.EMAIL).build());
	}

	private MvcResult forgot(String email) throws Exception {
		return mockMvc.perform(post("/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\"}")).andReturn();
	}

	private MvcResult reset(String token, String password, String confirm) throws Exception {
		return mockMvc.perform(post("/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\",\"newPasswordConfirm\":\""
				+ confirm + "\"}")).andReturn();
	}

	private int loginStatus(String email, String password) throws Exception {
		return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
			.andReturn().getResponse().getStatus();
	}

	private List<PasswordResetRequested> requestedEvents() {
		return events.stream(PasswordResetRequested.class).toList();
	}

	private String lastToken() {
		List<PasswordResetRequested> all = requestedEvents();
		return all.get(all.size() - 1).token();
	}

	private long refreshTokenRowsOf(String userId) {
		return (Long) em.createQuery("select count(r) from RefreshToken r where r.userId = :userId")
			.setParameter("userId", userId).getSingleResult();
	}

	private long tokenRowsOf(String userId) {
		return (Long) em.createQuery("select count(t) from PasswordResetToken t where t.userId = :userId")
			.setParameter("userId", userId).getSingleResult();
	}

	private String errorCodeOf(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.error.code");
	}

	private String errorMessageOf(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.error.message");
	}

	// ---------- 재설정 메일 요청 ----------

	@Test
	void 가입된_이메일로_요청하면_해시만_저장하고_메일_이벤트를_발행한다() throws Exception {
		String email = uniqueEmail();
		User user = createEmailUser(email);

		MvcResult result = forgot(email);

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getContentAsString()).isEmpty();
		assertThat(requestedEvents()).hasSize(1);
		PasswordResetRequested event = requestedEvents().get(0);
		assertThat(event.email()).isEqualTo(email);
		assertThat(event.token()).hasSize(43).matches("[A-Za-z0-9_-]+"); // 32바이트 → URL에 안전한 43자

		List<PasswordResetToken> rows = tokenRepository.findAll().stream()
			.filter(row -> row.getUserId().equals(user.getId())).toList();
		assertThat(rows).hasSize(1);
		PasswordResetToken row = rows.get(0);
		assertThat(row.getTokenHash()).isEqualTo(TokenHasher.sha256(event.token())).isNotEqualTo(event.token());
		assertThat(Duration.between(Instant.now(), row.getExpiresAt()))
			.isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));
		assertThat(row.getUsedAt()).isNull();
	}

	@Test
	void 가입되지_않은_이메일도_같은_응답을_주고_아무것도_만들지_않는다() throws Exception {
		String knownEmail = uniqueEmail();
		createEmailUser(knownEmail);
		long rowsBefore = tokenRepository.count();

		MvcResult known = forgot(knownEmail);
		int eventsAfterKnown = requestedEvents().size();
		MvcResult unknown = forgot(uniqueEmail());

		// 상태 코드와 본문이 같아서 응답으로는 가입 여부를 알 수 없다
		assertThat(unknown.getResponse().getStatus()).isEqualTo(known.getResponse().getStatus()).isEqualTo(200);
		assertThat(unknown.getResponse().getContentAsString()).isEqualTo(known.getResponse().getContentAsString());
		assertThat(requestedEvents()).hasSize(eventsAfterKnown); // 없는 이메일은 이벤트(메일)가 없다
		assertThat(tokenRepository.count()).isEqualTo(rowsBefore + 1); // 토큰은 가입된 이메일 것만 생긴다
	}

	@Test
	void Google로만_가입한_계정에는_메일을_보내지_않고_같은_응답을_준다() throws Exception {
		String email = "social-" + UUID.randomUUID() + "@gmail.com";
		userRepository.saveAndFlush(User.builder().email(email).nickname("소셜").provider(AuthProvider.GOOGLE)
			.providerId("g-" + UUID.randomUUID()).build());

		MvcResult result = forgot(email);

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(requestedEvents()).isEmpty();
	}

	@Test
	void 이메일은_대소문자를_무시하고_찾는다() throws Exception {
		String email = uniqueEmail();
		createEmailUser(email);

		forgot(email.toUpperCase());

		assertThat(requestedEvents()).hasSize(1);
		assertThat(requestedEvents().get(0).email()).isEqualTo(email);
	}

	@Test
	void 이메일_형식이_틀리면_VALIDATION_ERROR다() throws Exception {
		MvcResult result = forgot("not-an-email");

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorCodeOf(result)).isEqualTo("VALIDATION_ERROR");
		assertThat(requestedEvents()).isEmpty();
	}

	@Test
	void 새로_요청하면_이전_링크는_쓸_수_없다() throws Exception {
		String email = uniqueEmail();
		createEmailUser(email);
		forgot(email);
		String firstToken = lastToken();
		forgot(email);
		String secondToken = lastToken();

		assertThat(secondToken).isNotEqualTo(firstToken);
		MvcResult withOld = reset(firstToken, NEW_PASSWORD, NEW_PASSWORD);
		assertThat(withOld.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorCodeOf(withOld)).isEqualTo("INVALID_RESET_TOKEN");
		assertThat(reset(secondToken, NEW_PASSWORD, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void 같은_이메일은_1시간에_3번까지만_요청할_수_있다() throws Exception {
		String email = uniqueEmail();
		createEmailUser(email);

		for (int i = 0; i < 3; i++) {
			assertThat(forgot(email).getResponse().getStatus()).as("%d번째", i + 1).isEqualTo(200);
		}
		MvcResult blocked = forgot(email);

		assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
		assertThat(errorCodeOf(blocked)).isEqualTo("TOO_MANY_REQUESTS");
		// 윈도우가 끝날 때까지 남은 시간이다 (방금 시작한 윈도우라 1시간에 가깝다. 정확한 값은 시계를 조작하는 AuthRateLimiterTest에서 검증)
		assertThat(Long.parseLong(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isBetween(3500L, 3600L);
	}

	@Test
	void 가입되지_않은_이메일도_같은_횟수_제한을_받아서_429로_가입_여부가_드러나지_않는다() throws Exception {
		String unknownEmail = uniqueEmail();

		for (int i = 0; i < 3; i++) {
			assertThat(forgot(unknownEmail).getResponse().getStatus()).isEqualTo(200);
		}

		assertThat(forgot(unknownEmail).getResponse().getStatus()).isEqualTo(429);
	}

	// ---------- 비밀번호 재설정 ----------

	@Test
	void 재설정하면_새_비밀번호로만_로그인되고_모든_기기가_로그아웃된다() throws Exception {
		String email = uniqueEmail();
		User user = createEmailUser(email);
		AuthResult existingSession = authService.login(new LoginRequest(email, OLD_PASSWORD), "10.4.0.1");
		assertThat(refreshTokenRowsOf(user.getId())).isEqualTo(1); // 재설정 전에는 기존 세션이 살아 있다
		forgot(email);

		MvcResult result = reset(lastToken(), NEW_PASSWORD, NEW_PASSWORD);

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		// 새 로그인보다 먼저 확인해야 한다. 로그인하면 단일 디바이스 정책으로 기존 토큰이 어차피 지워져서,
		// 그 뒤에 확인하면 재설정이 토큰을 지우지 않아도 통과해 버린다
		assertThat(refreshTokenRowsOf(user.getId())).isZero();
		assertThatThrownBy(() -> authService.refresh(existingSession.refreshToken()))
			.isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));
		assertThat(loginStatus(email, NEW_PASSWORD)).isEqualTo(200);
		assertThat(loginStatus(email, OLD_PASSWORD)).isEqualTo(401);
	}

	@Test
	void 재설정이_끝나면_해당_사용자의_재설정_토큰은_모두_정리된다() throws Exception {
		String email = uniqueEmail();
		User user = createEmailUser(email);
		forgot(email);

		reset(lastToken(), NEW_PASSWORD, NEW_PASSWORD);

		assertThat(tokenRowsOf(user.getId())).isZero();
	}

	@Test
	void 링크는_한_번만_사용할_수_있다() throws Exception {
		String email = uniqueEmail();
		createEmailUser(email);
		forgot(email);
		String token = lastToken();
		assertThat(reset(token, NEW_PASSWORD, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);

		MvcResult again = reset(token, "Another3#Password", "Another3#Password");

		assertThat(again.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorCodeOf(again)).isEqualTo("INVALID_RESET_TOKEN");
		// 두 번째 시도의 비밀번호는 적용되지 않았다
		assertThat(loginStatus(email, NEW_PASSWORD)).isEqualTo(200);
	}

	@Test
	void 만료된_링크는_쓸_수_없다() throws Exception {
		String email = uniqueEmail();
		createEmailUser(email);
		forgot(email);
		String token = lastToken();
		em.flush();
		em.createNativeQuery("update password_reset_tokens set expires_at = now() - interval '1 minute'").executeUpdate();
		em.clear();

		MvcResult result = reset(token, NEW_PASSWORD, NEW_PASSWORD);

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorCodeOf(result)).isEqualTo("INVALID_RESET_TOKEN");
		assertThat(loginStatus(email, OLD_PASSWORD)).isEqualTo(200); // 비밀번호는 그대로
	}

	@Test
	void 존재하지_않는_토큰은_같은_오류다() throws Exception {
		MvcResult result = reset("garbage-token", NEW_PASSWORD, NEW_PASSWORD);

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorCodeOf(result)).isEqualTo("INVALID_RESET_TOKEN");
	}

	@Test
	void 비밀번호_규칙을_어기거나_확인이_다르면_거부하고_링크는_그대로_쓸_수_있다() throws Exception {
		String email = uniqueEmail();
		createEmailUser(email);
		forgot(email);
		String token = lastToken();

		MvcResult weak = reset(token, "weakpassword", "weakpassword");
		assertThat(weak.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorCodeOf(weak)).isEqualTo("VALIDATION_ERROR");
		assertThat(errorMessageOf(weak)).contains("대문자", "소문자", "숫자", "특수문자");

		MvcResult mismatch = reset(token, NEW_PASSWORD, "Different3#Password");
		assertThat(mismatch.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorMessageOf(mismatch)).isEqualTo("비밀번호가 일치하지 않습니다.");

		// 검증에 실패해도 토큰은 소모되지 않아 올바른 비밀번호로 다시 시도할 수 있다
		assertThat(reset(token, NEW_PASSWORD, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void 한글_비밀번호가_72바이트를_넘으면_거부한다() throws Exception {
		String email = uniqueEmail();
		createEmailUser(email);
		forgot(email);
		String tooLong = "Aa1!" + "가".repeat(25); // 글자 수는 통과하지만 79바이트

		MvcResult result = reset(lastToken(), tooLong, tooLong);

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorCodeOf(result)).isEqualTo("VALIDATION_ERROR");
	}

	@Test
	void 토큰이_비어_있으면_VALIDATION_ERROR다() throws Exception {
		MvcResult result = reset("", NEW_PASSWORD, NEW_PASSWORD);

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorCodeOf(result)).isEqualTo("VALIDATION_ERROR");
	}

	@Test
	void 탈퇴한_계정의_링크는_쓸_수_없다() throws Exception {
		String email = uniqueEmail();
		User user = createEmailUser(email);
		forgot(email);
		String token = lastToken();
		user.softDelete();
		userRepository.saveAndFlush(user);

		MvcResult result = reset(token, NEW_PASSWORD, NEW_PASSWORD);

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorCodeOf(result)).isEqualTo("INVALID_RESET_TOKEN");
	}
}
