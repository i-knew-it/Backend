package com.example.iknewit.user;

import java.util.concurrent.ThreadLocalRandom;

// 닉네임 규칙: 2~20자, 한글(완성형)·영문·숫자·밑줄만 허용. 중복은 허용
public final class Nickname {

	public static final String REGEX = "^[가-힣a-zA-Z0-9_]{2,20}$";
	public static final String MESSAGE = "닉네임은 2~20자의 한글, 영문, 숫자, 밑줄(_)만 사용할 수 있습니다.";

	private static final int MAX_LENGTH = 20;
	private static final int MIN_LENGTH = 2;

	private Nickname() {
	}

	// 소셜 계정의 표시 이름을 닉네임 규칙에 맞게 정리한다. 쓸 수 있는 글자가 부족하면 "사용자1234" 형태로 대체
	public static String fromDisplayName(String displayName) {
		String cleaned = displayName == null ? "" : displayName.replaceAll("[^가-힣a-zA-Z0-9_]", "");
		if (cleaned.length() > MAX_LENGTH) {
			cleaned = cleaned.substring(0, MAX_LENGTH);
		}
		if (cleaned.length() < MIN_LENGTH) {
			return "사용자" + ThreadLocalRandom.current().nextInt(1000, 10000);
		}
		return cleaned;
	}
}
