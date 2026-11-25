package com.example.iknewit.note;

import com.example.iknewit.common.BaseTimeEntity;
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
@Table(name = "learning_notes")
public class LearningNote extends BaseTimeEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "answer_id", nullable = false, unique = true, length = 26)
	private String answerId;

	// JOIN 없이 403 접근 제어를 하기 위한 직접 참조
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "user_id", nullable = false, length = 26)
	private String userId;

	// 마크다운 (생성 전 PENDING 상태에서는 null)
	@Column(columnDefinition = "text")
	private String content;

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(nullable = false, columnDefinition = "note_status")
	private NoteStatus status;

	@Builder
	private LearningNote(String answerId, String userId) {
		this.answerId = answerId;
		this.userId = userId;
		this.status = NoteStatus.PENDING;
	}
}
