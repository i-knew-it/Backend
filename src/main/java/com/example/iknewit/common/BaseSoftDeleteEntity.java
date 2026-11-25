package com.example.iknewit.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;

// created_at + updated_at + deleted_at 테이블용 (users, answers)
@Getter
@MappedSuperclass
public abstract class BaseSoftDeleteEntity extends BaseTimeEntity {

	// soft delete용. null이면 삭제되지 않은 상태
	@Column(name = "deleted_at")
	private Instant deletedAt;

	public void softDelete() {
		this.deletedAt = Instant.now();
	}

	public void restore() {
		this.deletedAt = null;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}
}
