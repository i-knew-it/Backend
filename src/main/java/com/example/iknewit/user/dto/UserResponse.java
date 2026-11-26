package com.example.iknewit.user.dto;

import com.example.iknewit.user.AuthProvider;
import com.example.iknewit.user.User;

// 내 정보 응답. 비밀번호 해시, 소셜 제공자 ID, 삭제 시각 같은 내부 값은 노출하지 않는다
public record UserResponse(
	String id,
	String email,
	String nickname,
	AuthProvider provider,
	boolean onboardingCompleted,
	String timezone
) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getProvider(),
			user.isOnboardingCompleted(), user.getTimezone());
	}
}
