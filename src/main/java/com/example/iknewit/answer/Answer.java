package com.example.iknewit.answer;

import com.example.iknewit.common.BaseSoftDeleteEntity;
import com.example.iknewit.common.Ulid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "answers")
public class Answer extends BaseSoftDeleteEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	// JOIN 없이 403 접근 제어를 하기 위한 직접 참조
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "user_id", nullable = false, length = 26)
	private String userId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "question_id", nullable = false, length = 26)
	private String questionId;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(nullable = false, columnDefinition = "answer_status")
	private AnswerStatus status;

	// 피드백 최초 확인 시각 (이탈 시 자동 완료 처리 기준)
	@Column(name = "feedback_viewed_at")
	private Instant feedbackViewedAt;

	// 답변일 (사용자 타임존 기준)
	@Column(name = "answered_date", nullable = false)
	private LocalDate answeredDate;

	@Builder
	private Answer(String userId, String questionId, String content, LocalDate answeredDate) {
		this.userId = userId;
		this.questionId = questionId;
		this.content = content;
		this.answeredDate = answeredDate;
		this.status = AnswerStatus.SUBMITTED;
	}
}
