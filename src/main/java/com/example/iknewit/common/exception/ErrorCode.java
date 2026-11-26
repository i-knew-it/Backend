package com.example.iknewit.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// 서버가 message를 직접 내려준다 (프론트-백 협의 사항)
@Getter
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증이 만료되었습니다. 다시 로그인해주세요."),
	TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다. 다시 로그인해주세요."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
	// 소셜 로그인 (프론트로 리다이렉트할 때 error 쿼리 값으로 사용)
	EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "인증되지 않은 이메일 계정은 사용할 수 없습니다."),
	OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다. 다시 시도해주세요.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}
}
