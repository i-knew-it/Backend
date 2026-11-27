package com.example.iknewit.auth.dto;

import com.example.iknewit.auth.PasswordPolicy;
import com.example.iknewit.user.Nickname;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
	@NotBlank(message = "이메일을 입력해주세요.")
	@Email(message = "올바른 이메일 형식이 아닙니다.")
	@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
	String email,

	@NotBlank(message = "비밀번호를 입력해주세요.")
	@Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
	String password,

	@NotBlank(message = "비밀번호 확인을 입력해주세요.")
	String passwordConfirm,

	@NotBlank(message = "닉네임을 입력해주세요.")
	@Pattern(regexp = Nickname.REGEX, message = Nickname.MESSAGE)
	String nickname
) {
}
