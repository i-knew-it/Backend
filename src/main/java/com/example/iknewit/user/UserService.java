package com.example.iknewit.user;

import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorCode;
import com.example.iknewit.user.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public UserResponse getMe(String userId) {
		// Access Token은 DB를 확인하지 않으므로, 토큰이 유효해도 그 사이 탈퇴(soft delete)한 계정일 수 있다.
		// 이 경우 인증이 유효하지 않은 것으로 보고 401을 준다
		User user = userRepository.findByIdAndDeletedAtIsNull(userId)
			.orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
		return UserResponse.from(user);
	}
}
