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
@Table(name = "chat_messages")
public class ChatMessage extends BaseCreatedEntity {

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
	@Column(nullable = false, columnDefinition = "chat_role")
	private ChatRole role;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Builder
	private ChatMessage(Answer answer, ChatRole role, String content) {
		this.answer = answer;
		this.role = role;
		this.content = content;
	}
}
