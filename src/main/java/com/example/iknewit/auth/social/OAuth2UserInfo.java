package com.example.iknewit.auth.social;

import com.example.iknewit.user.AuthProvider;

// 제공자(Google, 이후 카카오)마다 다른 사용자 정보 응답 구조를 공통 형태로 맞추는 인터페이스
public interface OAuth2UserInfo {

	AuthProvider provider();

	// 제공자 측 사용자 고유 ID
	String providerId();

	String email();

	boolean emailVerified();

	String displayName();
}
