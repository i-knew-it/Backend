package com.example.iknewit.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	// 아직 폐기되지 않은 토큰만 원자적으로 폐기. 반환값 0이면 이미 폐기됨(동시 사용 또는 재사용)
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update RefreshToken r set r.revokedAt = :now where r.tokenHash = :tokenHash and r.revokedAt is null")
	int revoke(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from RefreshToken r where r.tokenHash = :tokenHash")
	int deleteByTokenHash(@Param("tokenHash") String tokenHash);

	// 새 로그인 시 기존 세션 제거(단일 디바이스), 토큰 재사용 감지 시 전체 무효화에 사용
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from RefreshToken r where r.userId = :userId")
	int deleteAllByUserId(@Param("userId") String userId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from RefreshToken r where r.expiresAt < :now")
	int deleteExpired(@Param("now") Instant now);
}
