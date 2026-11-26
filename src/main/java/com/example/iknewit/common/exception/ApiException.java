package com.example.iknewit.common.exception;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

	private final ErrorCode errorCode;
	// 429 응답의 Retry-After 헤더용 (초). 해당 없으면 null
	private final Long retryAfterSeconds;

	public ApiException(ErrorCode errorCode) {
		this(errorCode, errorCode.getMessage(), null);
	}

	public ApiException(ErrorCode errorCode, String message) {
		this(errorCode, message, null);
	}

	public ApiException(ErrorCode errorCode, String message, Long retryAfterSeconds) {
		super(message);
		this.errorCode = errorCode;
		this.retryAfterSeconds = retryAfterSeconds;
	}
}
