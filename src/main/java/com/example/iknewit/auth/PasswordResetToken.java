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
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseCreatedEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "user_id", nullable = false, length = 26)
	private String userId;

	// 재설정 토큰의 SHA-256 해시(hex 64자). 원문은 저장하지 않는다 (메일 링크에만 존재)
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	// 사용된 시각. 값이 있으면 다시 쓸 수 없다
	@Column(name = "used_at")
	private Instant usedAt;

	@Builder
	private PasswordResetToken(String userId, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}
}
