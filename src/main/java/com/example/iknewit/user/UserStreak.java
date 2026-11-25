package com.example.iknewit.user;

import com.example.iknewit.common.BaseTimeEntity;
import com.example.iknewit.common.Ulid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_streaks")
public class UserStreak extends BaseTimeEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "user_id", nullable = false, unique = true, length = 26)
	private String userId;

	@Column(name = "current_streak", nullable = false)
	private int currentStreak;

	@Column(name = "longest_streak", nullable = false)
	private int longestStreak;

	// 마지막 학습 완료일 (사용자 타임존 기준)
	@Column(name = "last_learned_date")
	private LocalDate lastLearnedDate;

	@Builder
	private UserStreak(String userId) {
		this.userId = userId;
		this.currentStreak = 0;
		this.longestStreak = 0;
	}
}
