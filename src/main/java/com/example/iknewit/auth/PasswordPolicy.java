package com.example.iknewit.auth;

import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;

// 가입과 비밀번호 재설정이 같은 규칙을 쓰도록 한곳에 둔다
public final class PasswordPolicy {

	// 영문 대문자·소문자·숫자·특수문자(ASCII 기호) 각 1개 이상, 공백 불가, 8~72자
	// 특수문자를 ASCII 기호로 한정한 이유: 한글 등이 "특수문자"로 인정되는 허점을 막기 위해
	public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!-/:-@\\[-`{-~])\\S{8,72}$";
	public static final String MESSAGE =
		"비밀번호는 8~72자이며 영문 대문자, 소문자, 숫자, 특수문자를 각각 1개 이상 포함하고 공백이 없어야 합니다.";

	private static final int BCRYPT_MAX_BYTES = 72;

	private PasswordPolicy() {
	}

	// bcrypt는 72바이트까지만 반영하므로(한글은 글자당 3바이트) 글자 수와 별개로 바이트도 검증
	public static void requireWithinBcryptLimit(String password) {
		if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "비밀번호가 너무 깁니다. 72바이트 이하로 입력해주세요.");
		}
	}

	public static void requireConfirmed(String password, String confirm) {
		if (!password.equals(confirm)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "비밀번호가 일치하지 않습니다.");
		}
	}
}
