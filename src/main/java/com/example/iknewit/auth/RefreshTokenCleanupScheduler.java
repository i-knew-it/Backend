package com.example.iknewit.auth;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 만료된 Refresh Token 행을 매일 새벽에 삭제한다.
// 폐기(revoked)된 행도 만료 전에는 재사용 감지에 필요하므로 지우지 않고, 만료 시점에 함께 정리된다.
@Component
public class RefreshTokenCleanupScheduler {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);

	private final RefreshTokenRepository refreshTokenRepository;

	public RefreshTokenCleanupScheduler(RefreshTokenRepository refreshTokenRepository) {
		this.refreshTokenRepository = refreshTokenRepository;
	}

	@Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
	@Transactional
	public int cleanUp() {
		int deleted = refreshTokenRepository.deleteExpired(Instant.now());
		log.info("만료된 Refresh Token {}건 삭제", deleted);
		return deleted;
	}
}
