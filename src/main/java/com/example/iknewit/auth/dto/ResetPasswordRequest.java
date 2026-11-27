package com.example.iknewit.auth.dto;

import com.example.iknewit.auth.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
	// 메일 링크의 ?token= 값
	@NotBlank(message = "재설정 토큰이 필요합니다.")
	String token,

	@NotBlank(message = "새 비밀번호를 입력해주세요.")
	@Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
	String newPassword,

	@NotBlank(message = "새 비밀번호 확인을 입력해주세요.")
	String newPasswordConfirm
) {
}
