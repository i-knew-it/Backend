package com.example.iknewit.common.exception;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

// Security 필터 단계(컨트롤러 이전)에서 같은 에러 형식으로 응답하기 위한 유틸
public final class ErrorResponseWriter {

	private ErrorResponseWriter() {
	}

	public static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getStatus().value());
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"error\":{\"code\":\"" + errorCode.name()
			+ "\",\"message\":\"" + escape(errorCode.getMessage()) + "\"}}");
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
