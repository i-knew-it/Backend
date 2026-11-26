package com.example.iknewit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

// 기본 제한값(로그인 5회 실패 / IP 분당 20회)으로 무차별 대입 방어를 검증한다.
// 제한 기록은 서버 메모리에 남으므로 테스트마다 서로 다른 IP와 이메일을 쓴다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthRateLimitTest {

	private static final String PASSWORD = "Password123!";

	@Autowired
	MockMvc mockMvc;

	private String uniqueEmail() {
		return "rl-" + UUID.randomUUID() + "@example.com";
	}

	private MvcResult login(String email, String password, String ip) throws Exception {
		return mockMvc.perform(post("/auth/login").with(request -> {
			request.setRemoteAddr(ip);
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn();
	}

	private MvcResult signup(String email, String ip) throws Exception {
		return mockMvc.perform(post("/auth/signup").with(request -> {
			request.setRemoteAddr(ip);
			return request;
		}).contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"passwordConfirm\":\""
				+ PASSWORD + "\",\"nickname\":\"테스터\"}")).andReturn();
	}

	private String errorCodeOf(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.error.code");
	}

	@Test
	void 같은_이메일과_IP에서_5번_실패하면_6번째부터_429로_차단한다() throws Exception {
		String ip = "10.1.0.1";
		String email = uniqueEmail();
		signup(email, ip);

		for (int i = 0; i < 5; i++) {
			MvcResult failed = login(email, "Wrong-password1!", ip);
			assertThat(failed.getResponse().getStatus()).as("%d번째 실패", i + 1).isEqualTo(401);
		}

		MvcResult blocked = login(email, "Wrong-password1!", ip);
		assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
		assertThat(errorCodeOf(blocked)).isEqualTo("TOO_MANY_REQUESTS");
		assertThat(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
		assertThat(Long.parseLong(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isBetween(1L, 900L);
		assertThat((String) JsonPath.read(blocked.getResponse().getContentAsString(), "$.error.message"))
			.contains("15분");
	}

	@Test
	void 차단되면_올바른_비밀번호로도_로그인할_수_없다() throws Exception {
		String ip = "10.1.0.2";
		String email = uniqueEmail();
		signup(email, ip);
		for (int i = 0; i < 5; i++) {
			login(email, "Wrong-password1!", ip);
		}

		MvcResult correctButBlocked = login(email, PASSWORD, ip);

		assertThat(correctButBlocked.getResponse().getStatus()).isEqualTo(429);
	}

	@Test
	void 다른_IP에서는_같은_이메일로_로그인할_수_있다() throws Exception {
		String email = uniqueEmail();
		signup(email, "10.1.0.3");
		for (int i = 0; i < 5; i++) {
			login(email, "Wrong-password1!", "10.1.0.3");
		}
		assertThat(login(email, "Wrong-password1!", "10.1.0.3").getResponse().getStatus()).isEqualTo(429);

		// 공격자가 일부러 남의 계정을 잠그더라도 본인은 다른 IP에서 로그인할 수 있다
		assertThat(login(email, PASSWORD, "10.1.0.4").getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void 실패_후_성공하면_실패_횟수가_초기화된다() throws Exception {
		String ip = "10.1.0.5";
		String email = uniqueEmail();
		signup(email, ip);
		for (int i = 0; i < 4; i++) {
			login(email, "Wrong-password1!", ip);
		}

		assertThat(login(email, PASSWORD, ip).getResponse().getStatus()).isEqualTo(200);

		// 초기화되었으므로 다시 4번 실패해도 차단되지 않는다
		for (int i = 0; i < 4; i++) {
			assertThat(login(email, "Wrong-password1!", ip).getResponse().getStatus()).isEqualTo(401);
		}
	}

	@Test
	void 같은_IP에서_서로_다른_이메일을_번갈아_시도해도_분당_20회를_넘으면_차단한다() throws Exception {
		String ip = "10.1.0.6";

		for (int i = 0; i < 20; i++) {
			assertThat(login(uniqueEmail(), "Wrong-password1!", ip).getResponse().getStatus())
				.as("%d번째", i + 1).isEqualTo(401);
		}

		MvcResult blocked = login(uniqueEmail(), "Wrong-password1!", ip);
		assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
		assertThat(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
		// 가입도 같은 IP 제한을 공유한다
		assertThat(signup(uniqueEmail(), ip).getResponse().getStatus()).isEqualTo(429);
	}

	@Test
	void 다른_IP는_IP_제한의_영향을_받지_않는다() throws Exception {
		for (int i = 0; i < 21; i++) {
			login(uniqueEmail(), "Wrong-password1!", "10.1.0.7");
		}

		assertThat(login(uniqueEmail(), "Wrong-password1!", "10.1.0.8").getResponse().getStatus()).isEqualTo(401);
	}
}
