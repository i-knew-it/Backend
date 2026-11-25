package com.example.iknewit.notification;

import com.example.iknewit.common.BaseTimeEntity;
import com.example.iknewit.common.Ulid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_notification_settings")
public class UserNotificationSetting extends BaseTimeEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "user_id", nullable = false, unique = true, length = 26)
	private String userId;

	@Column(nullable = false)
	private boolean enabled;

	// 알림 시각 (사용자 로컬 시간 기준)
	@Column(name = "notify_at", nullable = false)
	private LocalTime notifyAt;

	@Builder
	private UserNotificationSetting(String userId, LocalTime notifyAt) {
		this.userId = userId;
		this.notifyAt = notifyAt;
		this.enabled = true;
	}
}
