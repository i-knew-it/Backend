package com.example.iknewit.user;

import com.example.iknewit.common.BaseSoftDeleteEntity;
import com.example.iknewit.common.Ulid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User extends BaseSoftDeleteEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	// 소셜 전용 계정은 null 가능
	@Column(length = 255)
	private String email;

	// 소셜 계정은 null
	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(nullable = false, columnDefinition = "auth_provider")
	private AuthProvider provider;

	@Column(name = "provider_id", length = 255)
	private String providerId;

	@Column(nullable = false, length = 50)
	private String timezone;

	@Column(name = "onboarding_completed", nullable = false)
	private boolean onboardingCompleted;

	@Builder
	private User(String email, String passwordHash, String name, AuthProvider provider,
		String providerId, String timezone) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.name = name;
		this.provider = provider;
		this.providerId = providerId;
		this.timezone = timezone != null ? timezone : "Asia/Seoul";
		this.onboardingCompleted = false;
	}
}
