package com.example.iknewit.question;

import com.example.iknewit.common.BaseCreatedEntity;
import com.example.iknewit.common.LevelType;
import com.example.iknewit.common.Ulid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
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
@Table(name = "questions")
public class Question extends BaseCreatedEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	@Column(name = "concept_tag", nullable = false, length = 50)
	private String conceptTag;

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(nullable = false, columnDefinition = "level_type")
	private LevelType difficulty;

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(nullable = false, columnDefinition = "question_source")
	private QuestionSource source;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	// 채점 기준 (jsonb). 구조가 바뀌어도 스키마 변경 없이 대응
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "evaluation_criteria", columnDefinition = "jsonb")
	private Map<String, Object> evaluationCriteria;

	@Builder
	private Question(String conceptTag, LevelType difficulty, QuestionSource source,
		String content, Map<String, Object> evaluationCriteria) {
		this.conceptTag = conceptTag;
		this.difficulty = difficulty;
		this.source = source;
		this.content = content;
		this.evaluationCriteria = evaluationCriteria;
	}
}
