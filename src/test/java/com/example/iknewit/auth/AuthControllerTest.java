package com.example.iknewit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.iknewit.auth.jwt.JwtProperties;
import com.example.iknewit.auth.jwt.JwtProvider;
import com.example.iknewit.user.Nickname;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

// 로컬 DB(docker compose)가 떠 있어야 하고 local 프로필로 실행. 트랜잭션 롤백이라 DB에 데이터가 남지 않는다
// 이 클래스는 많은 요청을 보내므로 IP 제한(분당 20회)은 크게 올려 둔다. IP/로그인 제한은 AuthRateLimitTest에서 검증
@SpringBootTest(properties = "auth.rate-limit.ip-max-per-minute=100000")
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

	private static final String PASSWORD = "Password123!";
	private static final String NICKNAME = "테스터";

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JwtProperties jwtProperties;
	@PersistenceContext
	EntityManager em;

	private String uniqueEmail() {
		return "test-" + UUID.randomUUID() + "@example.com";
	}

	private String signupBody(String email, String password, String passwordConfirm, String nickname) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"passwordConfirm\":\""
			+ passwordConfirm + "\",\"nickname\":\"" + nickname + "\"}";
	}

	private MvcResult signup(String email) throws Exception {
		return signupWith(signupBody(email, PASSWORD, PASSWORD, NICKNAME));
	}

	private MvcResult signupWith(String body) throws Exception {
		return mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
	}

	private MvcResult login(String email, String password) throws Exception {
		return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn();
	}

	private MvcResult refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/auth/token/refresh").cookie(new Cookie("refreshToken", refreshToken))).andReturn();
	}

	private String refreshTokenOf(MvcResult result) {
		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith("refreshToken=");
		return setCookie.substring("refreshToken=".length(), setCookie.indexOf(';'));
	}

	private String accessTokenOf(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
	}

	private String errorCodeOf(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.error.code");
	}

	private String errorMessageOf(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.error.message");
	}

	// 폐기 직후 유예 시간(10초)이 지난 상태를 만들기 위해 폐기 시각을 1분 앞당긴다
	private void ageRevokedTokens() {
		em.flush();
		em.createNativeQuery(
			"update refresh_tokens set revoked_at = revoked_at - interval '1 minute' where revoked_at is not null")
			.executeUpdate();
		em.clear();
	}

	private long refreshTokenRowsOf(String userId) {
		return (Long) em.createQuery("select count(r) from RefreshToken r where r.userId = :userId")
			.setParameter("userId", userId).getSingleResult();
	}

	private String userIdOf(MvcResult signupResult) throws Exception {
		return JsonPath.read(signupResult.getResponse().getContentAsString(), "$.user.id");
	}

	// ---------- 회원가입 ----------

	@Test
	void 회원가입하면_액세스_토큰과_보안_속성이_붙은_리프레시_쿠키를_받는다() throws Exception {
		String email = uniqueEmail();
		MvcResult result = signup(email);

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		assertThat(accessTokenOf(result)).isNotBlank();
		String body = result.getResponse().getContentAsString();
		assertThat((String) JsonPath.read(body, "$.user.email")).isEqualTo(email);
		assertThat((String) JsonPath.read(body, "$.user.nickname")).isEqualTo(NICKNAME);
		assertThat((String) JsonPath.read(body, "$.user.id")).hasSize(26);
		assertThat(body).doesNotContain("password").doesNotContain("Password");

		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/auth", "Max-Age=604800");
	}

	@Test
	void 이메일은_대소문자를_무시하고_중복을_막는다() throws Exception {
		String email = uniqueEmail();
		signup(email);

		MvcResult duplicate = signup(email.toUpperCase());
		assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
		assertThat(errorCodeOf(duplicate)).isEqualTo("EMAIL_ALREADY_EXISTS");

		// 대문자로 가입하면 소문자로 정규화되어 저장되므로, 소문자 이메일로도 로그인된다
		String upperEmail = uniqueEmail().toUpperCase();
		signup(upperEmail);
		assertThat(login(upperEmail.toLowerCase(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void 앞뒤_공백이_있는_이메일은_형식_오류로_거부한다() throws Exception {
		MvcResult result = signup(" " + uniqueEmail() + " ");

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorCodeOf(result)).isEqualTo("VALIDATION_ERROR");
	}

	@Test
	void 이메일_형식이_틀리면_VALIDATION_ERROR와_메시지를_준다() throws Exception {
		MvcResult result = signupWith(signupBody("not-an-email", PASSWORD, PASSWORD, NICKNAME));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorCodeOf(result)).isEqualTo("VALIDATION_ERROR");
		assertThat(errorMessageOf(result)).isEqualTo("올바른 이메일 형식이 아닙니다.");
	}

	@Test
	void JSON이_아니면_VALIDATION_ERROR다() throws Exception {
		mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void 비밀번호는_대문자_소문자_숫자_특수문자를_모두_포함해야_한다() throws Exception {
		String[] invalidPasswords = {
			"password123!", // 대문자 없음
			"PASSWORD123!", // 소문자 없음
			"Password!!!!", // 숫자 없음
			"Password1234", // 특수문자 없음
			"Password123한글", // 한글은 특수문자로 인정하지 않음
			"Pass word1!", // 공백 포함
			"Pa1!", // 너무 짧음
		};
		for (String password : invalidPasswords) {
			MvcResult result = signupWith(signupBody(uniqueEmail(), password, password, NICKNAME));
			assertThat(result.getResponse().getStatus()).as("비밀번호 '%s'", password).isEqualTo(400);
			assertThat(errorCodeOf(result)).isEqualTo("VALIDATION_ERROR");
			assertThat(errorMessageOf(result)).contains("대문자", "소문자", "숫자", "특수문자");
		}
	}

	@Test
	void 규칙을_지킨_비밀번호는_다양한_특수문자와_함께_허용한다() throws Exception {
		for (String password : new String[] {"Password1~", "Password1@", "Aa1_____", "Aa1[]{}()"}) {
			MvcResult result = signupWith(signupBody(uniqueEmail(), password, password, NICKNAME));
			assertThat(result.getResponse().getStatus()).as("비밀번호 '%s'", password).isEqualTo(201);
		}
	}

	@Test
	void 비밀번호_확인이_다르면_거부한다() throws Exception {
		MvcResult result = signupWith(signupBody(uniqueEmail(), PASSWORD, "Different123!", NICKNAME));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorCodeOf(result)).isEqualTo("VALIDATION_ERROR");
		assertThat(errorMessageOf(result)).isEqualTo("비밀번호가 일치하지 않습니다.");
	}

	@Test
	void 비밀번호_확인이_없으면_거부한다() throws Exception {
		MvcResult result = signupWith("{\"email\":\"" + uniqueEmail() + "\",\"password\":\"" + PASSWORD
			+ "\",\"nickname\":\"" + NICKNAME + "\"}");

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorMessageOf(result)).isEqualTo("비밀번호 확인을 입력해주세요.");
	}

	@Test
	void 한글_비밀번호가_72바이트를_넘으면_거부한다() throws Exception {
		// 글자 수(29자)는 통과하지만 바이트(79)가 72를 넘는다
		String password = "Aa1!" + "가".repeat(25);
		MvcResult result = signupWith(signupBody(uniqueEmail(), password, password, NICKNAME));

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorCodeOf(result)).isEqualTo("VALIDATION_ERROR");
	}

	@Test
	void 닉네임_규칙을_지키지_않으면_거부한다() throws Exception {
		for (String nickname : new String[] {"가", "홍 길동", "홍길동!", "a".repeat(21)}) {
			MvcResult result = signupWith(signupBody(uniqueEmail(), PASSWORD, PASSWORD, nickname));
			assertThat(result.getResponse().getStatus()).as("닉네임 '%s'", nickname).isEqualTo(400);
			assertThat(errorMessageOf(result)).isEqualTo(Nickname.MESSAGE);
		}
	}

	@Test
	void 닉네임이_없으면_거부한다() throws Exception {
		MvcResult result = signupWith("{\"email\":\"" + uniqueEmail() + "\",\"password\":\"" + PASSWORD
			+ "\",\"passwordConfirm\":\"" + PASSWORD + "\"}");

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(errorMessageOf(result)).isEqualTo("닉네임을 입력해주세요.");
	}

	@Test
	void 닉네임은_중복을_허용한다() throws Exception {
		assertThat(signup(uniqueEmail()).getResponse().getStatus()).isEqualTo(201);
		assertThat(signup(uniqueEmail()).getResponse().getStatus()).isEqualTo(201);
	}

	// ---------- 로그인 ----------

	@Test
	void 로그인_성공() throws Exception {
		String email = uniqueEmail();
		signup(email);

		MvcResult result = login(email, PASSWORD);
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(accessTokenOf(result)).isNotBlank();
		assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.user.nickname"))
			.isEqualTo(NICKNAME);
		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("HttpOnly", "Secure");
	}

	@Test
	void 비밀번호가_틀리거나_없는_이메일은_같은_INVALID_CREDENTIALS다() throws Exception {
		String email = uniqueEmail();
		signup(email);

		MvcResult wrongPassword = login(email, "Wrong-password1!");
		MvcResult unknownEmail = login(uniqueEmail(), PASSWORD);

		assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
		assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
		assertThat(errorCodeOf(wrongPassword)).isEqualTo("INVALID_CREDENTIALS");
		// 이메일 존재 여부가 응답으로 드러나지 않도록 메시지까지 같아야 한다
		assertThat(unknownEmail.getResponse().getContentAsString()).isEqualTo(wrongPassword.getResponse().getContentAsString());
	}

	@Test
	void 로그인에는_회원가입의_비밀번호_규칙을_적용하지_않는다() throws Exception {
		// 규칙에 맞지 않는 값도 400이 아니라 인증 실패(401)로 처리한다 (규칙은 가입 시에만 적용)
		MvcResult result = login(uniqueEmail(), "abc");

		assertThat(result.getResponse().getStatus()).isEqualTo(401);
	}

	// ---------- 단일 디바이스 ----------

	@Test
	void 다른_기기에서_로그인하면_이전_기기의_토큰은_무효가_된다() throws Exception {
		String email = uniqueEmail();
		MvcResult signup = signup(email);
		String deviceA = refreshTokenOf(signup);

		String deviceB = refreshTokenOf(login(email, PASSWORD));

		MvcResult oldDevice = refresh(deviceA);
		assertThat(oldDevice.getResponse().getStatus()).isEqualTo(401);
		assertThat(errorCodeOf(oldDevice)).isEqualTo("TOKEN_INVALID");
		// 새 기기는 계속 사용 가능
		assertThat(refresh(deviceB).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void 로그인할_때마다_사용자의_리프레시_토큰은_1개만_남는다() throws Exception {
		String email = uniqueEmail();
		String userId = userIdOf(signup(email));
		assertThat(refreshTokenRowsOf(userId)).isEqualTo(1);

		login(email, PASSWORD);
		login(email, PASSWORD);

		assertThat(refreshTokenRowsOf(userId)).isEqualTo(1);
	}

	// ---------- 토큰 갱신 ----------

	@Test
	void 토큰을_갱신하면_새_토큰이_발급된다() throws Exception {
		String oldRefresh = refreshTokenOf(signup(uniqueEmail()));

		MvcResult refreshed = refresh(oldRefresh);
		assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
		assertThat(accessTokenOf(refreshed)).isNotBlank();
		String newRefresh = refreshTokenOf(refreshed);
		assertThat(newRefresh).isNotEqualTo(oldRefresh);
		assertThat(refreshed.getResponse().getHeader(HttpHeaders.SET_COOKIE))
			.contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/auth");

		// 새 토큰으로 다시 갱신 가능
		assertThat(refresh(newRefresh).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void 교체_직후_10초_안의_같은_토큰_재사용은_동시_요청으로_보고_허용한다() throws Exception {
		String oldRefresh = refreshTokenOf(signup(uniqueEmail()));

		MvcResult first = refresh(oldRefresh);
		MvcResult second = refresh(oldRefresh); // 여러 탭/동시 요청 상황

		assertThat(first.getResponse().getStatus()).isEqualTo(200);
		assertThat(second.getResponse().getStatus()).isEqualTo(200);
		// 두 응답의 토큰은 모두 유효하다
		assertThat(refresh(refreshTokenOf(first)).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void 유예_시간이_지난_뒤_폐기된_토큰을_재사용하면_탈취로_보고_모든_토큰을_무효화한다() throws Exception {
		String oldRefresh = refreshTokenOf(signup(uniqueEmail()));
		String newRefresh = refreshTokenOf(refresh(oldRefresh));
		ageRevokedTokens();

		MvcResult reuse = refresh(oldRefresh);
		assertThat(reuse.getResponse().getStatus()).isEqualTo(401);
		assertThat(errorCodeOf(reuse)).isEqualTo("TOKEN_INVALID");

		// 정상 사용자의 최신 토큰도 함께 삭제된다
		assertThat(refresh(newRefresh).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void 갱신에_실패하면_쿠키를_만료시킨다() throws Exception {
		MvcResult garbage = refresh("garbage");
		assertThat(garbage.getResponse().getStatus()).isEqualTo(401);
		assertThat(errorCodeOf(garbage)).isEqualTo("TOKEN_INVALID");
		assertThat(garbage.getResponse().getHeader(HttpHeaders.SET_COOKIE))
			.contains("refreshToken=", "Max-Age=0", "Path=/auth", "HttpOnly");

		// 쿠키가 아예 없을 때도 동일
		MvcResult noCookie = mockMvc.perform(post("/auth/token/refresh")).andReturn();
		assertThat(noCookie.getResponse().getStatus()).isEqualTo(401);
		assertThat(errorCodeOf(noCookie)).isEqualTo("TOKEN_INVALID");
		assertThat(noCookie.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
	}

	@Test
	void 갱신에_성공하면_쿠키를_지우지_않고_새_값을_준다() throws Exception {
		MvcResult refreshed = refresh(refreshTokenOf(signup(uniqueEmail())));

		assertThat(refreshed.getResponse().getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Max-Age=0");
	}

	@Test
	void 액세스_토큰을_리프레시_자리에_쓰면_거부하고_쿠키를_만료시킨다() throws Exception {
		String accessToken = accessTokenOf(signup(uniqueEmail()));

		MvcResult result = refresh(accessToken);
		assertThat(result.getResponse().getStatus()).isEqualTo(401);
		assertThat(errorCodeOf(result)).isEqualTo("TOKEN_INVALID");
		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
	}

	// ---------- 로그아웃 ----------

	@Test
	void 로그아웃하면_토큰이_삭제되고_쿠키가_만료된다() throws Exception {
		String refreshToken = refreshTokenOf(signup(uniqueEmail()));

		MvcResult logout = mockMvc.perform(post("/auth/logout").cookie(new Cookie("refreshToken", refreshToken)))
			.andExpect(status().isOk()).andReturn();
		assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0", "Path=/auth", "HttpOnly");

		assertThat(refresh(refreshToken).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void 로그아웃은_쿠키가_없어도_200이다() throws Exception {
		mockMvc.perform(post("/auth/logout")).andExpect(status().isOk());
	}

	// ---------- 보호된 경로 ----------

	@Test
	void 토큰_없이_보호된_경로에_접근하면_UNAUTHORIZED다() throws Exception {
		mockMvc.perform(get("/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void 잘못된_액세스_토큰은_TOKEN_INVALID다() throws Exception {
		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
	}

	@Test
	void 만료된_액세스_토큰은_TOKEN_EXPIRED다() throws Exception {
		JwtProvider expiredIssuer = new JwtProvider(new JwtProperties(jwtProperties.secret(),
			Duration.ofSeconds(-60), Duration.ofSeconds(-60), Duration.ofSeconds(10)));
		String expired = expiredIssuer.createAccessToken("01ARZ3NDEKTSV4RRFFQ69G5FAV");

		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
	}

	@Test
	void 리프레시_토큰을_액세스_토큰처럼_쓰면_TOKEN_INVALID다() throws Exception {
		String refreshToken = refreshTokenOf(signup(uniqueEmail()));

		mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
	}

	@Test
	void 유효한_액세스_토큰이면_보안_필터를_통과한다() throws Exception {
		String accessToken = accessTokenOf(signup(uniqueEmail()));

		// 존재하지 않는 경로라서 401이 아니라 404면 인증은 통과한 것 (실제 API 응답은 UserControllerTest에서 검증)
		mockMvc.perform(get("/no-such-protected-path").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/no-such-protected-path")).andExpect(status().isUnauthorized());
	}

	@Test
	void JWT_인증은_세션을_만들지_않는다() throws Exception {
		MvcResult signup = signup(uniqueEmail());
		String accessToken = accessTokenOf(signup);

		MvcResult protectedCall = mockMvc.perform(get("/users/me")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)).andReturn();

		assertThat(signup.getRequest().getSession(false)).isNull();
		assertThat(protectedCall.getRequest().getSession(false)).isNull();
		assertThat(protectedCall.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
	}

	@Test
	void 공개_경로는_인증_없이_열려_있다() throws Exception {
		mockMvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void 프론트_origin의_CORS_프리플라이트는_쿠키_허용과_함께_통과한다() throws Exception {
		mockMvc.perform(options("/auth/token/refresh")
				.header(HttpHeaders.ORIGIN, "http://localhost:3000")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

		// 허용되지 않은 origin은 거부
		mockMvc.perform(options("/auth/token/refresh")
				.header(HttpHeaders.ORIGIN, "http://evil.example.com")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isForbidden());
	}
}
