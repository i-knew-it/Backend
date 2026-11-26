package com.example.iknewit.auth;

import com.example.iknewit.common.BaseCreatedEntity;
import com.example.iknewit.common.Ulid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseCreatedEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "user_id", nullable = false, length = 26)
	private String userId;

	// Refresh Token의 SHA-256 해시(hex 64자). 원문은 저장하지 않는다
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	// Rotation으로 폐기된 시각. 폐기된 토큰의 재사용은 탈취로 간주
	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Builder
	private RefreshToken(String userId, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}
}
