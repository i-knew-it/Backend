package com.example.iknewit.common.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
		ErrorCode code = e.getErrorCode();
		ResponseEntity.BodyBuilder response = ResponseEntity.status(code.getStatus());
		if (e.getRetryAfterSeconds() != null) {
			response.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
		}
		return response.body(ErrorResponse.of(code, e.getMessage()));
	}

	// @Valid 검증 실패: 첫 번째 필드 오류 메시지를 그대로 내려준다
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(error -> error.getDefaultMessage())
			.orElse(ErrorCode.VALIDATION_ERROR.getMessage());
		return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
			.body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, message));
	}

	// JSON 형식 오류, 본문 없음 등
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
		return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
			.body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR));
	}
}
