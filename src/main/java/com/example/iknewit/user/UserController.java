package com.example.iknewit.user;

import com.example.iknewit.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "내 정보")
@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@Operation(summary = "내 정보 조회",
		description = "Access Token의 사용자 정보를 반환합니다. 새로고침 후 /auth/token/refresh로 토큰을 복구한 뒤, "
			+ "또는 소셜 로그인 직후 사용자 정보를 얻는 용도입니다. 경로에 사용자 ID를 받지 않으므로 항상 본인 정보만 조회됩니다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@ApiResponse(responseCode = "401", description = "UNAUTHORIZED(토큰 없음), TOKEN_EXPIRED(만료), TOKEN_INVALID(유효하지 않음 또는 탈퇴한 계정)")
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal String userId) {
		return userService.getMe(userId);
	}
}
