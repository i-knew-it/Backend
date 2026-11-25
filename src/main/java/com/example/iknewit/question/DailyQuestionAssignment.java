package com.example.iknewit.question;

import com.example.iknewit.common.BaseCreatedEntity;
import com.example.iknewit.common.Ulid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "daily_question_assignments",
	uniqueConstraints = @UniqueConstraint(name = "uq_daily_assignment_user_date", columnNames = {"user_id", "assigned_date"}))
public class DailyQuestionAssignment extends BaseCreatedEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "user_id", nullable = false, length = 26)
	private String userId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "question_id", nullable = false, length = 26)
	private String questionId;

	// 배정일 (사용자 타임존 기준)
	@Column(name = "assigned_date", nullable = false)
	private LocalDate assignedDate;

	@Builder
	private DailyQuestionAssignment(String userId, String questionId, LocalDate assignedDate) {
		this.userId = userId;
		this.questionId = questionId;
		this.assignedDate = assignedDate;
	}
}
