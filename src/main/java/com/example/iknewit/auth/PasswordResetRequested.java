package com.example.iknewit.auth;

// 재설정 토큰이 발급되었음을 알리는 이벤트. 토큰 원문은 이 이벤트를 거쳐 메일로만 전달된다
// (DB에는 해시만 저장). 로그에 찍지 않도록 toString을 막는다
public record PasswordResetRequested(String email, String token) {

	@Override
	public String toString() {
		return "PasswordResetRequested[***]";
	}
}
