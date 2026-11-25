package com.example.iknewit.ai;

import com.example.iknewit.common.BaseCreatedEntity;
import com.example.iknewit.common.Ulid;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "ai_usage_logs")
public class AiUsageLog extends BaseCreatedEntity {

	@Id
	@Ulid
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 26)
	private String id;

	// 소프트 참조 (FK 없음) - 답변 삭제 후에도 비용 로그 보존
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "answer_id", length = 26)
	private String answerId;

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(nullable = false, columnDefinition = "ai_feature")
	private AiFeature feature;

	@Column(name = "prompt_tokens", nullable = false)
	private int promptTokens;

	@Column(name = "completion_tokens", nullable = false)
	private int completionTokens;

	@Column(name = "estimated_cost_usd", nullable = false, precision = 10, scale = 6)
	private BigDecimal estimatedCostUsd;

	@Builder
	private AiUsageLog(String answerId, AiFeature feature, int promptTokens, int completionTokens,
		BigDecimal estimatedCostUsd) {
		this.answerId = answerId;
		this.feature = feature;
		this.promptTokens = promptTokens;
		this.completionTokens = completionTokens;
		this.estimatedCostUsd = estimatedCostUsd != null ? estimatedCostUsd : BigDecimal.ZERO;
	}
}
