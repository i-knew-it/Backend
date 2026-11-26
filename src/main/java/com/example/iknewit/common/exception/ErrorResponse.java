package com.example.iknewit.common.exception;

// { "error": { "code": "...", "message": "..." } }
public record ErrorResponse(Error error) {

	public record Error(String code, String message) {
	}

	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(new Error(errorCode.name(), errorCode.getMessage()));
	}

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(new Error(errorCode.name(), message));
	}
}
