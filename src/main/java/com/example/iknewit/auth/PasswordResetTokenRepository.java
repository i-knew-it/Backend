package com.example.iknewit.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	// 아직 쓰지 않았고 만료되지 않은 토큰만 원자적으로 "사용됨" 처리. 반환값 1이면 이 요청이 토큰을 차지한 것,
	// 0이면 이미 사용됐거나 만료됨 (동시에 같은 토큰으로 두 번 재설정하는 것을 막는다)
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update PasswordResetToken t set t.usedAt = :now "
		+ "where t.tokenHash = :tokenHash and t.usedAt is null and t.expiresAt > :now")
	int markUsed(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

	// 새로 요청하면 이전 토큰을 모두 무효화(삭제), 재설정이 끝나면 남은 토큰 정리
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PasswordResetToken t where t.userId = :userId")
	int deleteAllByUserId(@Param("userId") String userId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PasswordResetToken t where t.expiresAt < :now")
	int deleteExpired(@Param("now") Instant now);
}
