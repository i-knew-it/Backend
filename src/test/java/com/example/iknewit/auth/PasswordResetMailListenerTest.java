package com.example.iknewit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.iknewit.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class PasswordResetMailListenerTest {

	private final JavaMailSender mailSender = mock(JavaMailSender.class);
	// 프론트 주소 끝의 슬래시는 링크에 중복 슬래시가 생기지 않게 정규화된다
	private final AppProperties properties = new AppProperties(
		"http://localhost:3000/", "/auth/callback", "/login", "/reset-password", "no-reply@example.com");
	private final PasswordResetMailListener listener = new PasswordResetMailListener(mailSender, properties);

	@Test
	void 재설정_링크가_담긴_메일을_보낸다() {
		listener.onRequested(new PasswordResetRequested("user@example.com", "tok_en-123"));

		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(captor.capture());
		SimpleMailMessage message = captor.getValue();
		assertThat(message.getTo()).containsExactly("user@example.com");
		assertThat(message.getFrom()).isEqualTo("no-reply@example.com");
		assertThat(message.getSubject()).contains("비밀번호 재설정");
		assertThat(message.getText())
			.contains("http://localhost:3000/reset-password?token=tok_en-123")
			.contains("30분")
			.contains("요청하지 않았다면");
	}

	@Test
	void 메일_발송이_실패해도_예외를_던지지_않는다() {
		doThrow(new MailSendException("connection refused for user@example.com"))
			.when(mailSender).send(any(SimpleMailMessage.class));

		// 메일 실패가 비밀번호 재설정 요청 자체를 실패시키면 안 된다
		assertThatCode(() -> listener.onRequested(new PasswordResetRequested("user@example.com", "tok_en-123")))
			.doesNotThrowAnyException();
	}

	@Test
	void 이벤트를_문자열로_찍어도_토큰과_이메일이_드러나지_않는다() {
		PasswordResetRequested event = new PasswordResetRequested("user@example.com", "tok_en-123");

		assertThat(event.toString()).doesNotContain("tok_en-123").doesNotContain("user@example.com");
	}
}
