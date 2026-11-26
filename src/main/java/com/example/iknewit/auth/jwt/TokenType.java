package com.example.iknewit.auth.jwt;

// 토큰 용도 구분. Refresh Token을 Access Token 자리에 쓰는 것을 막는다
public enum TokenType {
	ACCESS, REFRESH
}
