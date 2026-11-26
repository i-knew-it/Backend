package com.example.iknewit.auth.jwt;

import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import com.github.f4b6a3.ulid.UlidCreator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

	private static final String CLAIM_TYPE = "type";
	private static final int MIN_SECRET_BYTES = 32; // HS256 최소 키 길이(256bit)

	private final JwtProperties properties;
	private final SecretKey key;

	public JwtProvider(JwtProperties properties) {
		byte[] secret = properties.secret() == null ? new byte[0] : properties.secret().getBytes(StandardCharsets.UTF_8);
		// 비밀키를 안 넣고 기동하는 사고 방지: 없거나 짧으면 기동 자체를 실패시킨다
		if (secret.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("jwt.secret은 32바이트 이상이어야 합니다. 환경변수 JWT_SECRET을 확인하세요.");
		}
		this.properties = properties;
		this.key = Keys.hmacShaKeyFor(secret);
	}

	public String createAccessToken(String userId) {
		return build(userId, TokenType.ACCESS, properties.accessTokenExpiration()).token();
	}

	public IssuedToken createRefreshToken(String userId) {
		return build(userId, TokenType.REFRESH, properties.refreshTokenExpiration());
	}

	public Duration refreshTokenExpiration() {
		return properties.refreshTokenExpiration();
	}

	/**
	 * 서명, 만료, 용도를 검증하고 userId를 반환한다.
	 * 만료는 TOKEN_EXPIRED, 그 외(위조, 형식 오류, 용도 불일치)는 TOKEN_INVALID
	 */
	public String parseUserId(String token, TokenType expectedType) {
		try {
			Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
			if (!expectedType.name().equals(claims.get(CLAIM_TYPE, String.class)) || claims.getSubject() == null) {
				throw new ApiException(ErrorCode.TOKEN_INVALID);
			}
			return claims.getSubject();
		} catch (ExpiredJwtException e) {
			throw new ApiException(ErrorCode.TOKEN_EXPIRED);
		} catch (JwtException | IllegalArgumentException e) {
			throw new ApiException(ErrorCode.TOKEN_INVALID);
		}
	}

	private IssuedToken build(String userId, TokenType type, Duration lifetime) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(lifetime);
		String token = Jwts.builder()
			.subject(userId)
			.claim(CLAIM_TYPE, type.name())
			// jti: 같은 초에 발급해도 토큰이 항상 달라지도록 (token_hash UNIQUE 충돌 방지)
			.id(UlidCreator.getUlid().toString())
			.issuedAt(Date.from(now))
			.expiration(Date.from(expiresAt))
			.signWith(key)
			.compact();
		return new IssuedToken(token, expiresAt);
	}
}
