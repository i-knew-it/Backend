package com.example.iknewit.auth;

import com.example.iknewit.auth.dto.ForgotPasswordRequest;
import com.example.iknewit.auth.dto.ResetPasswordRequest;
import com.example.iknewit.auth.ratelimit.AuthRateLimiter;
import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import com.example.iknewit.user.AuthProvider;
import com.example.iknewit.user.Emails;
import com.example.iknewit.user.User;
import com.example.iknewit.user.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

	// 재설정 링크 유효 시간
	static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
	private static final int TOKEN_BYTES = 32; // 256bit 랜덤 → 추측 불가능

	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository tokenRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthRateLimiter rateLimiter;
	private final ApplicationEventPublisher eventPublisher;

	public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
		RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
		AuthRateLimiter rateLimiter, ApplicationEventPublisher eventPublisher) {
		this.userRepository = userRepository;
		this.tokenRepository = tokenRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.rateLimiter = rateLimiter;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * 재설정 메일 요청. 이메일이 가입되어 있든 아니든 호출자에게는 같은 결과(정상 반환)를 준다.
	 * 이메일 로그인 계정일 때만 토큰을 만들고 메일 발송 이벤트를 발행한다.
	 * 소셜 전용 계정(비밀번호 없음)과 없는 이메일은 아무 일도 하지 않는다.
	 */
	@Transactional
	public void requestReset(ForgotPasswordRequest request, String clientIp) {
		rateLimiter.checkIp(clientIp);
		String email = Emails.normalize(request.email());
		// 가입 여부와 무관하게 센다 (429 여부로 가입 여부가 드러나지 않도록)
		rateLimiter.checkPasswordResetRequest(email);

		userRepository.findByEmailAndDeletedAtIsNull(email)
			.filter(user -> user.getProvider() == AuthProvider.EMAIL && user.getPasswordHash() != null)
			.ifPresent(this::issueToken);
	}

	/**
	 * 토큰으로 비밀번호를 바꾼다. 성공하면 그 사용자의 모든 Refresh Token을 삭제해 다른 기기를 로그아웃시킨다.
	 * 비밀번호 검증은 토큰을 소모하기 전에 하므로, 규칙에 맞지 않는 비밀번호로 시도해도 링크는 그대로 쓸 수 있다.
	 */
	@Transactional
	public void resetPassword(ResetPasswordRequest request, String clientIp) {
		rateLimiter.checkIp(clientIp);
		PasswordPolicy.requireConfirmed(request.newPassword(), request.newPasswordConfirm());
		PasswordPolicy.requireWithinBcryptLimit(request.newPassword());

		String tokenHash = TokenHasher.sha256(request.token());
		PasswordResetToken stored = tokenRepository.findByTokenHash(tokenHash)
			.orElseThrow(() -> new ApiException(ErrorCode.INVALID_RESET_TOKEN));
		// 사용·만료 여부 확인과 사용 처리를 한 번의 UPDATE로 해서 같은 토큰의 동시 사용을 막는다
		if (tokenRepository.markUsed(tokenHash, Instant.now()) == 0) {
			throw new ApiException(ErrorCode.INVALID_RESET_TOKEN);
		}

		User user = userRepository.findByIdAndDeletedAtIsNull(stored.getUserId())
			.orElseThrow(() -> new ApiException(ErrorCode.INVALID_RESET_TOKEN));
		user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
		// 비밀번호가 바뀌었으므로 이전 비밀번호로 만들어진 세션은 모두 끊고, 남은 재설정 토큰도 정리한다
		refreshTokenRepository.deleteAllByUserId(user.getId());
		tokenRepository.deleteAllByUserId(user.getId());
	}

	private void issueToken(User user) {
		// 사용자당 유효한 토큰은 1개: 새로 요청하면 이전 링크는 쓸 수 없다
		tokenRepository.deleteAllByUserId(user.getId());

		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

		tokenRepository.save(PasswordResetToken.builder()
			.userId(user.getId())
			.tokenHash(TokenHasher.sha256(token))
			.expiresAt(Instant.now().plus(TOKEN_LIFETIME))
			.build());
		// 메일은 트랜잭션이 커밋된 뒤 비동기로 발송된다 (PasswordResetMailListener)
		eventPublisher.publishEvent(new PasswordResetRequested(user.getEmail(), token));
	}
}
