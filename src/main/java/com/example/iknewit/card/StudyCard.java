package com.example.iknewit.card;

import com.example.iknewit.common.BaseTimeEntity;
import com.example.iknewit.common.Ulid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
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
@Table(name = "study_cards",
	uniqueConstraints = @UniqueConstraint(name = "uq_study_cards_user_tag", columnNames = {"user_id", "concept_tag"}))
public class StudyCard extends BaseTimeEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "user_id", nullable = false, length = 26)
	private String userId;

	@Column(name = "concept_tag", nullable = false, length = 50)
	private String conceptTag;

	// 질문
	@Column(nullable = false, columnDefinition = "text")
	private String front;

	// 핵심 답변 + 빠뜨렸던 조건
	@Column(nullable = false, columnDefinition = "text")
	private String back;

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(name = "review_status", nullable = false, columnDefinition = "review_status")
	private ReviewStatus reviewStatus;

	// 마지막으로 틀리거나 빠뜨린 시각
	@Column(name = "last_error_at")
	private Instant lastErrorAt;

	@Builder
	private StudyCard(String userId, String conceptTag, String front, String back, Instant lastErrorAt) {
		this.userId = userId;
		this.conceptTag = conceptTag;
		this.front = front;
		this.back = back;
		this.lastErrorAt = lastErrorAt;
		this.reviewStatus = ReviewStatus.DEFAULT;
	}
}
