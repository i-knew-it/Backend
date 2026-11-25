package com.example.iknewit.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

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
