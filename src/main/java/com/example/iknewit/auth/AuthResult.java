package com.example.iknewit.auth;

import com.example.iknewit.auth.dto.UserSummary;

// 서비스 → 컨트롤러 전달용. refreshToken 원문은 쿠키로만 내려간다
public record AuthResult(String accessToken, String refreshToken, UserSummary user) {
}
