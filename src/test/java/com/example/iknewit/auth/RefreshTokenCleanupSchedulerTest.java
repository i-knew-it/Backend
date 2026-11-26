package com.example.iknewit.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.iknewit.user.AuthProvider;
import com.example.iknewit.user.User;
import com.example.iknewit.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RefreshTokenCleanupSchedulerTest {

	@Autowired
	RefreshTokenCleanupScheduler scheduler;
	@Autowired
	RefreshTokenRepository refreshTokenRepository;
	@Autowired
	UserRepository userRepository;

	private RefreshToken token(String userId, Instant expiresAt) {
		return refreshTokenRepository.saveAndFlush(RefreshToken.builder()
			.userId(userId)
			.tokenHash(TokenHasher.sha256(UUID.randomUUID().toString()))
			.expiresAt(expiresAt)
			.build());
	}

	@Test
	void 만료된_행만_삭제하고_유효한_행은_남긴다() {
		User user = userRepository.saveAndFlush(User.builder().email("cleanup-" + UUID.randomUUID() + "@example.com")
			.passwordHash("{bcrypt}x").nickname("정리테스트").provider(AuthProvider.EMAIL).build());
		RefreshToken expired = token(user.getId(), Instant.now().minus(Duration.ofDays(1)));
		RefreshToken valid = token(user.getId(), Instant.now().plus(Duration.ofDays(1)));

		int deleted = scheduler.cleanUp();

		assertThat(deleted).isGreaterThanOrEqualTo(1);
		assertThat(refreshTokenRepository.findById(expired.getId())).isEmpty();
		assertThat(refreshTokenRepository.findById(valid.getId())).isPresent();
	}
}
