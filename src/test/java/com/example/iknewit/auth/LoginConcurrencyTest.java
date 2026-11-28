package com.example.iknewit.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.iknewit.auth.dto.LoginRequest;
import com.example.iknewit.auth.dto.ResetPasswordRequest;
import com.example.iknewit.auth.social.OAuth2UserInfo;
import com.example.iknewit.auth.social.SocialAuthService;
import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import com.example.iknewit.user.AuthProvider;
import com.example.iknewit.user.User;
import com.example.iknewit.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로그인과 다른 인증 작업이 동시에 올 때의 경쟁 상태 검증. 서로 다른 트랜잭션이 필요하므로 @Transactional을 쓰지 않고,
 * 데이터가 실제로 커밋되므로 만든 사용자와 토큰은 테스트가 끝나면 직접 지운다. 로컬 DB와 local 프로필 필요.
 */
@SpringBootTest(properties = "auth.rate-limit.ip-max-per-minute=100000")
class LoginConcurrencyTest {

	private static final String OLD_PASSWORD = "OldPassword1!";
	private static final String NEW_PASSWORD = "NewPassword2@";
	private static final int ROUNDS = 12;

	@Autowired
	AuthService authService;
	@Autowired
	PasswordResetService passwordResetService;
	@Autowired
	UserRepository userRepository;
	@Autowired
	PasswordResetTokenRepository tokenRepository;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	JdbcTemplate jdbcTemplate;
	@Autowired
	PlatformTransactionManager transactionManager;
	@Autowired
	SocialAuthService socialAuthService;

	private final List<String> createdUserIds = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		for (String userId : createdUserIds) {
			jdbcTemplate.update("delete from password_reset_tokens where user_id = ?", userId);
			jdbcTemplate.update("delete from refresh_tokens where user_id = ?", userId);
			jdbcTemplate.update("delete from users where id = ?", userId);
		}
		createdUserIds.clear();
	}

	private record Outcome(Throwable error) {
		boolean succeeded() {
			return error == null;
		}
	}

	private User createUser() {
		User user = userRepository.saveAndFlush(User.builder()
			.email("login-race-" + UUID.randomUUID() + "@example.com")
			.passwordHash(passwordEncoder.encode(OLD_PASSWORD))
			.nickname("경쟁테스트")
			.provider(AuthProvider.EMAIL)
			.build());
		createdUserIds.add(user.getId());
		return user;
	}

	private long refreshTokenRows(String userId) {
		Long count = jdbcTemplate.queryForObject("select count(*) from refresh_tokens where user_id = ?", Long.class, userId);
		return count == null ? 0 : count;
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	// 모든 작업을 같은 순간에 시작시키고 결과(성공/예외)를 입력 순서대로 돌려준다
	private List<Outcome> race(List<Runnable> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Outcome>> futures = new ArrayList<>();
			for (Runnable task : tasks) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					try {
						task.run();
						return new Outcome(null);
					} catch (Throwable t) {
						return new Outcome(t);
					}
				}));
			}
			ready.await();
			start.countDown();
			List<Outcome> outcomes = new ArrayList<>();
			for (Future<Outcome> future : futures) {
				outcomes.add(future.get(30, TimeUnit.SECONDS));
			}
			return outcomes;
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void 옛_비밀번호_로그인과_재설정이_동시에_와도_재설정이_끝난_뒤에는_세션이_남지_않는다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			User user = createUser();
			String token = "login-race-token-" + UUID.randomUUID();
			tokenRepository.saveAndFlush(PasswordResetToken.builder()
				.userId(user.getId())
				.tokenHash(TokenHasher.sha256(token))
				.expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
				.build());
			String loginIp = "10.8." + round + ".1";
			// 로그인이 "재설정이 커밋되기 직전에 사용자를 읽는" 순간을 맞추려고, 라운드마다 로그인 시작을 조금씩 늦춘다
			// (재설정은 로그인보다 단계가 많아 같은 순간에 시작하면 항상 늦게 끝나서 경쟁이 드러나지 않는다)
			long loginDelayMillis = round * 12L; // 0~132ms

			List<Outcome> outcomes = race(List.of(
				() -> {
					sleep(loginDelayMillis);
					authService.login(new LoginRequest(user.getEmail(), OLD_PASSWORD), loginIp);
				},
				() -> passwordResetService.resetPassword(
					new ResetPasswordRequest(token, NEW_PASSWORD, NEW_PASSWORD), "10.8." + 0 + ".2")));

			Outcome login = outcomes.get(0);
			Outcome reset = outcomes.get(1);
			assertThat(reset.error()).as("%d번째 라운드: 재설정은 성공해야 함", round + 1).isNull();
			// 로그인은 재설정보다 먼저 처리되면 성공하고, 나중이면 옛 비밀번호라 거절된다. 그 외 예외는 없어야 한다
			if (!login.succeeded()) {
				assertThat(login.error()).as("%d번째 라운드: 로그인 오류", round + 1).isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
			}
			// 재설정이 성공했다면 옛 비밀번호로 만들어진 세션은 하나도 남아 있으면 안 된다
			assertThat(refreshTokenRows(user.getId())).as("%d번째 라운드: 재설정 후 남은 세션", round + 1).isZero();
		}
	}

	@Test
	void 토큰_갱신과_로그인이_동시에_와도_교착_없이_처리된다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			User user = createUser();
			AuthResult session = authService.login(new LoginRequest(user.getEmail(), OLD_PASSWORD), "10.10." + round + ".0");
			String loginIp = "10.10." + round + ".1";

			List<Outcome> outcomes = race(List.of(
				() -> authService.refresh(session.refreshToken()),
				() -> authService.login(new LoginRequest(user.getEmail(), OLD_PASSWORD), loginIp)));

			Outcome refresh = outcomes.get(0);
			Outcome login = outcomes.get(1);
			// 로그인은 항상 성공해야 한다. 갱신은 로그인이 먼저 기존 토큰을 지웠다면 TOKEN_INVALID로 거절될 수 있다.
			// 그 외 예외(교착, 잠금 실패 등)는 있으면 안 된다
			assertThat(login.error()).as("%d번째 라운드: 로그인", round + 1).isNull();
			if (!refresh.succeeded()) {
				assertThat(refresh.error()).as("%d번째 라운드: 갱신", round + 1).isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));
			}
			// 새 로그인이 끝났으므로 단일 디바이스 정책대로 세션은 1개만 남아야 한다
			assertThat(refreshTokenRows(user.getId())).as("%d번째 라운드: 남은 세션", round + 1).isEqualTo(1);
		}
	}

	@Test
	void 토큰_갱신과_비밀번호_재설정이_동시에_와도_교착_없이_처리된다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			User user = createUser();
			AuthResult session = authService.login(new LoginRequest(user.getEmail(), OLD_PASSWORD), "10.11." + round + ".0");
			String token = "refresh-race-token-" + UUID.randomUUID();
			tokenRepository.saveAndFlush(PasswordResetToken.builder()
				.userId(user.getId())
				.tokenHash(TokenHasher.sha256(token))
				.expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
				.build());
			String resetIp = "10.11." + round + ".1";

			List<Outcome> outcomes = race(List.of(
				() -> authService.refresh(session.refreshToken()),
				() -> passwordResetService.resetPassword(
					new ResetPasswordRequest(token, NEW_PASSWORD, NEW_PASSWORD), resetIp)));

			Outcome refresh = outcomes.get(0);
			Outcome reset = outcomes.get(1);
			assertThat(reset.error()).as("%d번째 라운드: 재설정", round + 1).isNull();
			if (!refresh.succeeded()) {
				assertThat(refresh.error()).as("%d번째 라운드: 갱신", round + 1).isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID));
			}
			// 재설정이 끝난 뒤에는 갱신이 먼저 끝났더라도 그 세션까지 모두 삭제되어야 한다
			assertThat(refreshTokenRows(user.getId())).as("%d번째 라운드: 재설정 후 남은 세션", round + 1).isZero();
		}
	}

	@Test
	void 토큰_갱신은_사용자_행_잠금이_풀릴_때까지_기다린다() throws Exception {
		// 갱신이 사용자 행을 잠그지 않으면 로그인의 "전체 삭제"가 갱신이 새로 넣은 토큰을 지우지 못해 이전 기기 토큰이 남는다.
		// 갱신이 로그인·재설정과 같은 잠금으로 직렬화되는지 확인한다 (로그인과 갱신이 겹치는 순간은 타이밍이 짧아 직접 만들 수 없다)
		User user = createUser();
		AuthResult session = authService.login(new LoginRequest(user.getEmail(), OLD_PASSWORD), "10.13.0.0");
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<?> holding = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				userRepository.findByEmailForUpdate(user.getEmail());
				locked.countDown();
				try {
					release.await(20, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			Future<AuthResult> refreshing = pool.submit(() -> authService.refresh(session.refreshToken()));
			sleep(1000);
			assertThat(refreshing.isDone()).as("잠금이 풀리기 전에는 갱신이 끝나면 안 됨").isFalse();

			release.countDown();
			holding.get(10, TimeUnit.SECONDS);
			assertThat(refreshing.get(10, TimeUnit.SECONDS)).isNotNull();
		} finally {
			release.countDown();
			pool.shutdownNow();
		}
	}

	@Test
	void 소셜_로그인은_사용자_행_잠금이_풀릴_때까지_기다린다() throws Exception {
		// 소셜 로그인도 로그인·갱신과 같은 잠금으로 직렬화되어야 단일 디바이스 정책이 동시 요청에서도 지켜진다
		String providerId = "google-race-" + UUID.randomUUID();
		User user = userRepository.saveAndFlush(User.builder()
			.email("social-race-" + UUID.randomUUID() + "@example.com")
			.nickname("소셜경쟁")
			.provider(AuthProvider.GOOGLE)
			.providerId(providerId)
			.build());
		createdUserIds.add(user.getId());
		OAuth2UserInfo info = new OAuth2UserInfo() {
			public AuthProvider provider() { return AuthProvider.GOOGLE; }
			public String providerId() { return providerId; }
			public String email() { return user.getEmail(); }
			public boolean emailVerified() { return true; }
			public String displayName() { return "소셜경쟁"; }
		};
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<?> holding = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				userRepository.findByEmailForUpdate(user.getEmail());
				locked.countDown();
				try {
					release.await(20, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			Future<AuthResult> loggingIn = pool.submit(() -> socialAuthService.loginOrSignup(info));
			sleep(1000);
			assertThat(loggingIn.isDone()).as("잠금이 풀리기 전에는 소셜 로그인이 끝나면 안 됨").isFalse();

			release.countDown();
			holding.get(10, TimeUnit.SECONDS);
			assertThat(loggingIn.get(10, TimeUnit.SECONDS)).isNotNull();
			assertThat(refreshTokenRows(user.getId())).isEqualTo(1);
		} finally {
			release.countDown();
			pool.shutdownNow();
		}
	}

	@Test
	void 오답_로그인은_사용자_행_잠금을_기다리지_않고_바로_거절된다() throws Exception {
		User user = createUser();
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService holder = Executors.newSingleThreadExecutor();
		try {
			// 다른 트랜잭션이 사용자 행 잠금을 쥐고 있는 상황
			Future<?> holding = holder.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				userRepository.findByEmailForUpdate(user.getEmail());
				locked.countDown();
				try {
					release.await(20, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			long startedAt = System.nanoTime();
			Throwable error = null;
			try {
				authService.login(new LoginRequest(user.getEmail(), "Wrong-password1!"), "10.12.0.1");
			} catch (Throwable t) {
				error = t;
			}
			long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

			assertThat(error).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
			// 잠금이 풀리길 기다렸다면 release 전까지(수 초) 끝나지 않았을 것이다
			assertThat(elapsedMillis).as("오답 로그인 처리 시간(ms)").isLessThan(3000);
			release.countDown();
			holding.get(10, TimeUnit.SECONDS);
		} finally {
			release.countDown();
			holder.shutdownNow();
		}
	}

	@Test
	void 같은_사용자가_동시에_로그인해도_Refresh_Token은_1개만_남는다() throws Exception {
		int threads = 3;
		for (int round = 0; round < ROUNDS; round++) {
			User user = createUser();
			List<Runnable> tasks = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				String ip = "10.9." + round + "." + i;
				tasks.add(() -> authService.login(new LoginRequest(user.getEmail(), OLD_PASSWORD), ip));
			}

			List<Outcome> outcomes = race(tasks);

			assertThat(outcomes).as("%d번째 라운드: 로그인은 모두 성공해야 함", round + 1)
				.allSatisfy(outcome -> assertThat(outcome.error()).isNull());
			// 단일 디바이스 정책: 마지막에 남는 세션은 1개여야 한다
			assertThat(refreshTokenRows(user.getId())).as("%d번째 라운드: 남은 세션", round + 1).isEqualTo(1);
		}
	}
}
