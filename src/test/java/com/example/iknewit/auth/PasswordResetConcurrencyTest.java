package com.example.iknewit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.example.iknewit.auth.dto.ForgotPasswordRequest;
import com.example.iknewit.auth.dto.ResetPasswordRequest;
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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 동시 요청 경쟁 상태 검증. 여러 스레드가 서로 다른 트랜잭션에서 실행되어야 하므로 @Transactional을 쓰지 않는다.
 * 따라서 데이터가 실제로 커밋되므로 만든 사용자와 토큰은 테스트가 끝나면 직접 지운다.
 * 커밋되면 메일 리스너(AFTER_COMMIT)가 실행되므로 실제 SMTP로 나가지 않게 JavaMailSender를 목으로 대체한다.
 * 로컬 DB가 떠 있어야 하고 local 프로필로 실행.
 */
@SpringBootTest(properties = "auth.rate-limit.ip-max-per-minute=100000")
class PasswordResetConcurrencyTest {

	private static final String OLD_PASSWORD = "OldPassword1!";
	private static final String PASSWORD_A = "RacePassA1!";
	private static final String PASSWORD_B = "RacePassB2@";
	private static final int ROUNDS = 8;

	@MockitoBean
	JavaMailSender mailSender;
	@Autowired
	PasswordResetService service;
	@Autowired
	UserRepository userRepository;
	@Autowired
	PasswordResetTokenRepository tokenRepository;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	JdbcTemplate jdbcTemplate;

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
			.email("race-" + UUID.randomUUID() + "@example.com")
			.passwordHash(passwordEncoder.encode(OLD_PASSWORD))
			.nickname("경쟁테스트")
			.provider(AuthProvider.EMAIL)
			.build());
		createdUserIds.add(user.getId());
		return user;
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
	void 같은_토큰으로_동시에_재설정하면_정확히_한_요청만_성공한다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			User user = createUser();
			String token = "race-token-" + UUID.randomUUID();
			tokenRepository.saveAndFlush(PasswordResetToken.builder()
				.userId(user.getId())
				.tokenHash(TokenHasher.sha256(token))
				.expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
				.build());

			List<Outcome> outcomes = race(List.of(
				() -> service.resetPassword(new ResetPasswordRequest(token, PASSWORD_A, PASSWORD_A), "10.5.0.1"),
				() -> service.resetPassword(new ResetPasswordRequest(token, PASSWORD_B, PASSWORD_B), "10.5.0.2")));

			assertThat(outcomes.stream().filter(Outcome::succeeded).count()).as("%d번째 라운드: 성공 수", round + 1).isEqualTo(1);
			Outcome failed = outcomes.stream().filter(o -> !o.succeeded()).findFirst().orElseThrow();
			assertThat(failed.error()).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_RESET_TOKEN));

			// 이긴 요청의 비밀번호만 적용되고 진 요청의 비밀번호는 적용되지 않는다
			String winnerPassword = outcomes.get(0).succeeded() ? PASSWORD_A : PASSWORD_B;
			String loserPassword = outcomes.get(0).succeeded() ? PASSWORD_B : PASSWORD_A;
			String hash = userRepository.findById(user.getId()).orElseThrow().getPasswordHash();
			assertThat(passwordEncoder.matches(winnerPassword, hash)).isTrue();
			assertThat(passwordEncoder.matches(loserPassword, hash)).isFalse();
		}
	}

	@Test
	void 재설정과_재설정_메일_요청이_동시에_와도_교착_없이_처리된다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			User user = createUser();
			String token = "mixed-token-" + UUID.randomUUID();
			tokenRepository.saveAndFlush(PasswordResetToken.builder()
				.userId(user.getId())
				.tokenHash(TokenHasher.sha256(token))
				.expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
				.build());
			ForgotPasswordRequest forgot = new ForgotPasswordRequest(user.getEmail());

			List<Outcome> outcomes = race(List.of(
				() -> service.resetPassword(new ResetPasswordRequest(token, PASSWORD_A, PASSWORD_A), "10.7.0.1"),
				() -> service.requestReset(forgot, "10.7.0.2")));

			// 재설정은 성공하거나, 메일 요청이 먼저 처리되어 이전 토큰이 지워졌다면 INVALID_RESET_TOKEN이다.
			// 그 외 예외(교착, 잠금 실패 등)는 있으면 안 된다. 메일 요청은 항상 성공해야 한다.
			Outcome reset = outcomes.get(0);
			Outcome request = outcomes.get(1);
			assertThat(request.error()).as("%d번째 라운드: 메일 요청", round + 1).isNull();
			if (!reset.succeeded()) {
				assertThat(reset.error()).as("%d번째 라운드: 재설정", round + 1).isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_RESET_TOKEN));
			}
		}
	}

	@Test
	void 같은_사용자가_동시에_재설정_메일을_요청해도_유효한_토큰은_1개만_남는다() throws Exception {
		int threads = 3; // 이메일당 시간당 3회 제한 안에서 동시에 요청
		for (int round = 0; round < ROUNDS; round++) {
			User user = createUser();
			ForgotPasswordRequest request = new ForgotPasswordRequest(user.getEmail());
			List<Runnable> tasks = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				String ip = "10.6." + round + "." + i;
				tasks.add(() -> service.requestReset(request, ip));
			}

			List<Outcome> outcomes = race(tasks);

			assertThat(outcomes).as("%d번째 라운드: 예외 없이 모두 응답해야 함", round + 1)
				.allSatisfy(outcome -> assertThat(outcome.error()).isNull());
			long activeTokens = tokenRepository.findAll().stream()
				.filter(token -> token.getUserId().equals(user.getId())).count();
			assertThat(activeTokens).as("%d번째 라운드: 남은 토큰 수", round + 1).isEqualTo(1);
		}
		// 실제로 커밋되었으므로 메일 발송 이벤트가 처리된다 (요청 수만큼)
		verify(mailSender, timeout(10_000).times(threads * ROUNDS)).send(any(SimpleMailMessage.class));
	}
}
