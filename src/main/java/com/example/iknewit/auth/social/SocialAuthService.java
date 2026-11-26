package com.example.iknewit.auth.social;

import com.example.iknewit.auth.AuthResult;
import com.example.iknewit.auth.AuthService;
import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import com.example.iknewit.user.Emails;
import com.example.iknewit.user.Nickname;
import com.example.iknewit.user.User;
import com.example.iknewit.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialAuthService {

	private final UserRepository userRepository;
	private final AuthService authService;

	public SocialAuthService(UserRepository userRepository, AuthService authService) {
		this.userRepository = userRepository;
		this.authService = authService;
	}

	/**
	 * 소셜 로그인 또는 최초 가입.
	 *  - 같은 (provider, providerId) 계정이 있으면 로그인
	 *  - 없으면 신규 가입. 단, 같은 이메일의 다른 계정이 이미 있으면 자동 연동하지 않고 거절한다
	 *    (이메일만 믿고 연동하면 계정 탈취에 악용될 수 있음)
	 *  - 이메일이 인증되지 않은 소셜 계정은 거절한다
	 */
	@Transactional
	public AuthResult loginOrSignup(OAuth2UserInfo info) {
		if (isBlank(info.providerId())) {
			throw new ApiException(ErrorCode.OAUTH_FAILED);
		}
		if (isBlank(info.email()) || !info.emailVerified()) {
			throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED);
		}
		String email = Emails.normalize(info.email());

		User user = userRepository
			.findByProviderAndProviderIdAndDeletedAtIsNull(info.provider(), info.providerId())
			.orElse(null);
		if (user == null) {
			if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
				throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
			}
			user = User.builder()
				.email(email)
				.nickname(Nickname.fromDisplayName(info.displayName()))
				.provider(info.provider())
				.providerId(info.providerId())
				.build();
			try {
				userRepository.saveAndFlush(user);
			} catch (DataIntegrityViolationException e) {
				// 동시 가입, 또는 삭제(soft delete)된 같은 소셜 계정이 남아 있는 경우
				throw new ApiException(ErrorCode.OAUTH_FAILED);
			}
		}
		return authService.startSession(user);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
