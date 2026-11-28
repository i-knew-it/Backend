package com.example.iknewit.auth;

import com.example.iknewit.auth.dto.LoginRequest;
import com.example.iknewit.auth.dto.SignupRequest;
import com.example.iknewit.auth.dto.UserSummary;
import com.example.iknewit.auth.jwt.IssuedToken;
import com.example.iknewit.auth.jwt.JwtProperties;
import com.example.iknewit.auth.jwt.JwtProvider;
import com.example.iknewit.auth.jwt.TokenType;
import com.example.iknewit.auth.ratelimit.AuthRateLimiter;
import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import com.example.iknewit.user.AuthProvider;
import com.example.iknewit.user.Emails;
import com.example.iknewit.user.User;
import com.example.iknewit.user.UserRepository;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;
	private final JwtProperties jwtProperties;
	private final AuthRateLimiter rateLimiter;
	// 존재하지 않는 이메일로 로그인할 때도 같은 시간이 걸리도록 비교에 쓰는 더미 해시 (이메일 존재 여부 노출 방지)
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
		PasswordEncoder passwordEncoder, JwtProvider jwtProvider, JwtProperties jwtProperties,
		AuthRateLimiter rateLimiter) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtProvider = jwtProvider;
		this.jwtProperties = jwtProperties;
		this.rateLimiter = rateLimiter;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	@Transactional
	public AuthResult signup(SignupRequest request, String clientIp) {
		rateLimiter.checkIp(clientIp);
		PasswordPolicy.requireConfirmed(request.password(), request.passwordConfirm());
		PasswordPolicy.requireWithinBcryptLimit(request.password());
		String email = Emails.normalize(request.email());
		if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
			throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		User user = User.builder()
			.email(email)
			.passwordHash(passwordEncoder.encode(request.password()))
			.nickname(request.nickname())
			.provider(AuthProvider.EMAIL)
			.build();
		try {
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			// 동시 가입으로 위의 중복 검사를 통과한 경우 DB 유니크 인덱스(uq_users_email_active)가 막는다
			throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		return issueTokens(user);
	}

	@Transactional
	public AuthResult login(LoginRequest request, String clientIp) {
		String email = Emails.normalize(request.email());
		// 차단 여부를 먼저 확인한다 (차단 중에는 비밀번호가 맞아도 로그인시키지 않음)
		rateLimiter.checkIp(clientIp);
		rateLimiter.checkLogin(clientIp, email);

		// 1단계: 잠금 없이 비밀번호를 확인한다. 틀린 요청은 여기서 끝나므로 잠금을 잡지 않는다
		// (오답 로그인을 반복해서 남의 계정의 정상 로그인·재설정을 막거나 DB 커넥션을 붙잡는 것을 방지).
		// 소셜 전용 계정(해시 없음)과 가입되지 않은 이메일은 더미 해시로 비교해 응답 시간과 결과가 같다
		String storedHash = userRepository.findPasswordHashByEmail(email).orElse(null);
		boolean matches = passwordEncoder.matches(request.password(), storedHash != null ? storedHash : dummyPasswordHash);
		if (storedHash == null || !matches) {
			throw failLogin(clientIp, email);
		}

		// 2단계: 맞은 요청만 사용자 행을 잠그고 최신 해시로 다시 확인한 뒤 같은 트랜잭션에서 세션을 만든다.
		// 1단계와 잠금 사이에 비밀번호 재설정이 끝났을 수 있고, 같은 사용자의 로그인이 동시에 올 수 있어서다.
		//  1) 옛 해시로 통과한 로그인이 재설정 뒤에 세션을 만들어 "재설정하면 모든 기기 로그아웃"이 깨지는 문제
		//  2) 동시 로그인으로 Refresh Token이 여러 개 남는(단일 디바이스 정책 위반) 문제
		User user = userRepository.findByEmailForUpdate(email).orElse(null);
		if (user == null || user.getPasswordHash() == null
			|| !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw failLogin(clientIp, email);
		}
		rateLimiter.recordSuccess(clientIp, email);
		return startSession(user);
	}

	private ApiException failLogin(String clientIp, String email) {
		rateLimiter.recordFailure(clientIp, email);
		return new ApiException(ErrorCode.INVALID_CREDENTIALS);
	}

	/**
	 * 새 로그인 세션 시작. 사용자당 1개 디바이스만 로그인할 수 있으므로 기존 Refresh Token을 모두 삭제하고 새로 발급한다.
	 * (이전 기기는 Access Token이 만료돼 갱신을 시도하는 시점에 로그아웃된다)
	 */
	@Transactional
	public AuthResult startSession(User user) {
		refreshTokenRepository.deleteAllByUserId(user.getId());
		return issueTokens(user);
	}

	/**
	 * Refresh Token Rotation: 기존 토큰을 폐기하고 새 토큰 쌍을 발급한다.
	 *
	 * 이미 폐기된 토큰이 다시 오면:
	 *  - 폐기 직후 유예 시간(기본 10초) 안이면 동시 요청(여러 탭, 연속 호출)으로 보고 새 토큰을 발급한다.
	 *  - 그보다 늦으면 탈취로 보고 해당 사용자의 모든 Refresh Token을 삭제한다.
	 * 삭제가 예외로 롤백되지 않도록 noRollbackFor 지정
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public AuthResult refresh(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new ApiException(ErrorCode.TOKEN_INVALID);
		}
		// 서명 검증을 통과한 토큰만 여기까지 온다. 로그인·재설정과 같은 순서("사용자 행 → 토큰 행")로 사용자 행을 먼저 잠근다.
		// 잠그지 않으면 갱신이 새 토큰을 넣는 도중에 끼어든 로그인의 "전체 삭제"가 그 새 토큰을 보지 못해(삭제 시작 시점의
		// 스냅샷 기준) 이전 기기의 토큰이 살아남는다 (단일 디바이스 정책 위반)
		String userId = jwtProvider.parseUserId(refreshToken, TokenType.REFRESH);
		// 기준 시각은 잠금을 기다리기 전에 읽는다. 대기한 시간이 재사용 유예(기본 10초) 판정에 더해지면, 유예 안에 도착한
		// 정상 요청이 탈취로 오인되어 사용자의 모든 토큰이 삭제될 수 있다 (요청이 도착한 시각 기준으로 판정)
		Instant now = Instant.now();
		userRepository.findByIdForUpdate(userId).orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
		String tokenHash = TokenHasher.sha256(refreshToken);

		RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
			.orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
		if (stored.getExpiresAt().isBefore(now)) {
			throw new ApiException(ErrorCode.TOKEN_EXPIRED);
		}

		if (stored.getRevokedAt() != null) {
			rejectUnlessWithinGrace(stored.getRevokedAt(), now, userId);
		} else if (refreshTokenRepository.revoke(tokenHash, now) == 0) {
			// 조회 직후 다른 요청이 먼저 폐기한 경우: 그 폐기 시각을 기준으로 동시 요청인지 판단
			RefreshToken latest = refreshTokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
			if (latest.getRevokedAt() == null) {
				throw new ApiException(ErrorCode.TOKEN_INVALID);
			}
			rejectUnlessWithinGrace(latest.getRevokedAt(), now, userId);
		}

		User user = userRepository.findByIdAndDeletedAtIsNull(stored.getUserId())
			.orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
		return issueTokens(user);
	}

	// 해당 디바이스의 토큰만 삭제. 토큰이 없거나 이미 없어도 성공 처리(멱등)
	@Transactional
	public void logout(String refreshToken) {
		if (refreshToken != null && !refreshToken.isBlank()) {
			refreshTokenRepository.deleteByTokenHash(TokenHasher.sha256(refreshToken));
		}
	}

	private void rejectUnlessWithinGrace(Instant revokedAt, Instant now, String userId) {
		boolean withinGrace = !revokedAt.plus(jwtProperties.refreshReuseGrace()).isBefore(now);
		if (!withinGrace) {
			refreshTokenRepository.deleteAllByUserId(userId);
			throw new ApiException(ErrorCode.TOKEN_INVALID);
		}
	}

	private AuthResult issueTokens(User user) {
		String accessToken = jwtProvider.createAccessToken(user.getId());
		IssuedToken refreshToken = jwtProvider.createRefreshToken(user.getId());
		refreshTokenRepository.save(RefreshToken.builder()
			.userId(user.getId())
			.tokenHash(TokenHasher.sha256(refreshToken.token()))
			.expiresAt(refreshToken.expiresAt())
			.build());
		return new AuthResult(accessToken, refreshToken.token(),
			new UserSummary(user.getId(), user.getEmail(), user.getNickname()));
	}
}
