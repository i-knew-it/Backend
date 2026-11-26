package com.example.iknewit.auth.dto;

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

	// 영문 대문자·소문자·숫자·특수문자(ASCII 기호) 각 1개 이상, 공백 불가, 8~72자
	// 특수문자를 ASCII 기호로 한정한 이유: 한글 등이 "특수문자"로 인정되는 허점을 막기 위해
	@NotBlank(message = "비밀번호를 입력해주세요.")
	@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[!-/:-@\\[-`{-~])\\S{8,72}$",
		message = "비밀번호는 8~72자이며 영문 대문자, 소문자, 숫자, 특수문자를 각각 1개 이상 포함하고 공백이 없어야 합니다.")
	String password,

	@NotBlank(message = "비밀번호 확인을 입력해주세요.")
	String passwordConfirm,

	@NotBlank(message = "닉네임을 입력해주세요.")
	@Pattern(regexp = Nickname.REGEX, message = Nickname.MESSAGE)
	String nickname
) {
}
