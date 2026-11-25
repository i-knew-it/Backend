package com.example.iknewit.answer;

import com.example.iknewit.common.BaseCreatedEntity;
import com.example.iknewit.common.Ulid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "answer_feedback_items")
public class AnswerFeedbackItem extends BaseCreatedEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "answer_id", nullable = false)
	private Answer answer;

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(nullable = false, columnDefinition = "judgment_type")
	private Judgment judgment;

	// 사용자 답변 중 해당 부분 (MISSING은 null 가능)
	@Column(name = "target_text", columnDefinition = "text")
	private String targetText;

	// 판정 이유
	@Column(nullable = false, columnDefinition = "text")
	private String reason;

	@Builder
	private AnswerFeedbackItem(Answer answer, Judgment judgment, String targetText, String reason) {
		this.answer = answer;
		this.judgment = judgment;
		this.targetText = targetText;
		this.reason = reason;
	}
}
