package com.example.iknewit.notification;

import com.example.iknewit.common.BaseTimeEntity;
import com.example.iknewit.common.Ulid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_fcm_tokens")
public class UserFcmToken extends BaseTimeEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "user_id", nullable = false, length = 26)
	private String userId;

	@Column(nullable = false, unique = true, length = 512)
	private String token;

	@Column(name = "device_info", length = 255)
	private String deviceInfo;

	@Builder
	private UserFcmToken(String userId, String token, String deviceInfo) {
		this.userId = userId;
		this.token = token;
		this.deviceInfo = deviceInfo;
	}
}
