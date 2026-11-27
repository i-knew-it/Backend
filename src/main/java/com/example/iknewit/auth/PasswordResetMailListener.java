package com.example.iknewit.auth;

import com.example.iknewit.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 재설정 메일 발송. 토큰이 DB에 커밋된 뒤(AFTER_COMMIT), 요청과 분리된 스레드(@Async)에서 보낸다.
 * - 커밋 전에 메일이 나가면 롤백된 토큰의 링크가 메일로 전달되는 문제가 생긴다
 * - 비동기로 보내야 메일 서버 지연이 응답 시간에 드러나지 않고, 메일 실패가 요청 실패로 이어지지 않는다
 */
@Component
public class PasswordResetMailListener {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetMailListener.class);

	private final JavaMailSender mailSender;
	private final AppProperties appProperties;

	public PasswordResetMailListener(JavaMailSender mailSender, AppProperties appProperties) {
		this.mailSender = mailSender;
		this.appProperties = appProperties;
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRequested(PasswordResetRequested event) {
		try {
			mailSender.send(buildMessage(event));
		} catch (RuntimeException e) {
			// 이메일 주소와 토큰이 로그에 남지 않도록 예외 메시지는 찍지 않고 종류만 기록
			log.warn("비밀번호 재설정 메일 발송 실패: {}", e.getClass().getSimpleName());
		}
	}

	SimpleMailMessage buildMessage(PasswordResetRequested event) {
		String link = UriComponentsBuilder.fromUriString(appProperties.primaryFrontendUrl())
			.path(appProperties.passwordResetPath())
			.queryParam("token", event.token())
			.build().toUriString();

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(appProperties.mailFrom());
		message.setTo(event.email());
		message.setSubject("[아는 줄 알았어] 비밀번호 재설정 안내");
		message.setText("""
			안녕하세요. 아는 줄 알았어입니다.

			아래 링크에서 비밀번호를 새로 설정할 수 있습니다. 링크는 30분 동안만 유효하고 한 번만 사용할 수 있습니다.

			%s

			비밀번호 재설정을 요청하지 않았다면 이 메일을 무시하셔도 됩니다. 비밀번호는 바뀌지 않습니다.
			""".formatted(link));
		return message;
	}
}
