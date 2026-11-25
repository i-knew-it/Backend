package com.example.iknewit.user;

import com.example.iknewit.common.BaseTimeEntity;
import com.example.iknewit.common.LevelType;
import com.example.iknewit.common.Ulid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "user_levels",
	uniqueConstraints = @UniqueConstraint(name = "uq_user_levels_user_tag", columnNames = {"user_id", "concept_tag"}))
public class UserLevel extends BaseTimeEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "user_id", nullable = false, length = 26)
	private String userId;

	// 분야 태그 (OS, Network, DB, Java, Spring 등)
	@Column(name = "concept_tag", nullable = false, length = 50)
	private String conceptTag;

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(nullable = false, columnDefinition = "level_type")
	private LevelType level;

	@Builder
	private UserLevel(String userId, String conceptTag, LevelType level) {
		this.userId = userId;
		this.conceptTag = conceptTag;
		this.level = level;
	}
}
