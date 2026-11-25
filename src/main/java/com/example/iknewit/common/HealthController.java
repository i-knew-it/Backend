package com.example.iknewit.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 서버 생존 확인용. Actuator 없이 단순 응답만 반환 (DB 등 외부 의존성은 확인하지 않음)
@Tag(name = "Health")
@RestController
public class HealthController {

	@Operation(summary = "서버 상태 확인")
	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "ok");
	}
}
