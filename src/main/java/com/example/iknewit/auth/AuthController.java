package com.example.iknewit.auth;

import com.example.iknewit.auth.dto.AuthResponse;
import com.example.iknewit.auth.dto.LoginRequest;
import com.example.iknewit.auth.dto.SignupRequest;
import com.example.iknewit.auth.dto.TokenResponse;
import com.example.iknewit.common.exception.ApiException;
import com.example.iknewit.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입, 로그인, 토큰 갱신, 로그아웃 (사용자당 1개 디바이스만 로그인)")
@SecurityRequirements // 이 컨트롤러는 Bearer 토큰 없이 호출 (Swagger의 자물쇠 표시 제거)
@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;
	private final RefreshCookieFactory cookieFactory;

	public AuthController(AuthService authService, RefreshCookieFactory cookieFactory) {
		this.authService = authService;
		this.cookieFactory = cookieFactory;
	}

	@Operation(summary = "이메일 회원가입",
		description = "Access Token은 본문으로, Refresh Token은 HttpOnly 쿠키(Set-Cookie)로 내려갑니다. "
			+ "비밀번호는 8~72자이며 영문 대문자·소문자·숫자·특수문자를 각각 1개 이상 포함해야 합니다.")
	@ApiResponse(responseCode = "201", description = "가입 성공")
	@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: 요청 값 오류(비밀번호 규칙, 비밀번호 확인 불일치, 닉네임 규칙 등)")
	@ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_EXISTS: 이미 가입된 이메일")
	@ApiResponse(responseCode = "429", description = "TOO_MANY_REQUESTS: 같은 IP의 시도 횟수 초과 (Retry-After 헤더)")
	@PostMapping("/signup")
	public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
		return withRefreshCookie(HttpStatus.CREATED, authService.signup(request, http.getRemoteAddr()));
	}

	@Operation(summary = "이메일 로그인",
		description = "Access Token은 본문으로, Refresh Token은 HttpOnly 쿠키로 내려갑니다. "
			+ "다른 기기에서 로그인되어 있었다면 그 기기는 로그아웃됩니다.")
	@ApiResponse(responseCode = "200", description = "로그인 성공")
	@ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS: 이메일 없음 또는 비밀번호 불일치")
	@ApiResponse(responseCode = "429", description = "TOO_MANY_REQUESTS: 연속 실패(5회) 또는 IP 시도 초과 (Retry-After 헤더)")
	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return withRefreshCookie(HttpStatus.OK, authService.login(request, http.getRemoteAddr()));
	}

	@Operation(summary = "Access Token 갱신",
		description = "쿠키의 Refresh Token으로 새 Access Token을 발급하고, Refresh Token도 새로 교체(Rotation)합니다. "
			+ "실패하면 쿠키도 함께 만료(삭제)시킵니다.")
	@ApiResponse(responseCode = "200", description = "갱신 성공",
		content = @Content(schema = @Schema(implementation = TokenResponse.class)))
	@ApiResponse(responseCode = "401", description = "TOKEN_EXPIRED 또는 TOKEN_INVALID (쿠키가 삭제됩니다)",
		content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping("/token/refresh")
	public ResponseEntity<?> refresh(
		@CookieValue(name = RefreshCookieFactory.NAME, required = false) String refreshToken) {
		try {
			AuthResult result = authService.refresh(refreshToken);
			return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookieFactory.create(result.refreshToken()).toString())
				.body(new TokenResponse(result.accessToken()));
		} catch (ApiException e) {
			// 쓸모없어진 쿠키를 브라우저가 계속 보내지 않도록 같이 만료시킨다
			return ResponseEntity.status(e.getErrorCode().getStatus())
				.header(HttpHeaders.SET_COOKIE, cookieFactory.expire().toString())
				.body(ErrorResponse.of(e.getErrorCode(), e.getMessage()));
		}
	}

	@Operation(summary = "로그아웃", description = "서버의 Refresh Token을 삭제하고 쿠키를 만료시킵니다. 이미 로그아웃된 상태여도 200입니다.")
	@ApiResponse(responseCode = "200", description = "로그아웃 완료")
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
		@CookieValue(name = RefreshCookieFactory.NAME, required = false) String refreshToken) {
		authService.logout(refreshToken);
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookieFactory.expire().toString())
			.build();
	}

	private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthResult result) {
		return ResponseEntity.status(status)
			.header(HttpHeaders.SET_COOKIE, cookieFactory.create(result.refreshToken()).toString())
			.body(new AuthResponse(result.accessToken(), result.user()));
	}
}
