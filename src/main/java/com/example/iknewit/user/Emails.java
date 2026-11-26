package com.example.iknewit.user;

import java.util.Locale;

public final class Emails {

	private Emails() {
	}

	// 이메일은 앞뒤 공백 제거 후 소문자로 저장·비교한다 (DB 유니크 인덱스가 대소문자를 구분하므로)
	public static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
