package com.example.iknewit.auth.dto;

// 회원가입/로그인 응답. Refresh Token은 본문이 아니라 HttpOnly 쿠키로 전달
public record AuthResponse(String accessToken, UserSummary user) {
}
