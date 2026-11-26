package com.example.iknewit.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NicknameTest {

	private boolean valid(String nickname) {
		return nickname.matches(Nickname.REGEX);
	}

	@Test
	void 한글_영문_숫자_밑줄_2에서_20자는_허용한다() {
		assertThat(valid("홍길동")).isTrue();
		assertThat(valid("jinho_92")).isTrue();
		assertThat(valid("가나")).isTrue();
		assertThat(valid("a".repeat(20))).isTrue();
	}

	@Test
	void 길이가_맞지_않거나_허용하지_않는_글자는_거부한다() {
		assertThat(valid("가")).isFalse();
		assertThat(valid("a".repeat(21))).isFalse();
		assertThat(valid("홍 길동")).isFalse(); // 공백
		assertThat(valid("홍길동!")).isFalse(); // 특수문자
		assertThat(valid("ㅋㅋㅋ")).isFalse(); // 자모만 있는 경우
		assertThat(valid("<script>")).isFalse();
	}

	@Test
	void 소셜_표시_이름을_닉네임_규칙에_맞게_정리한다() {
		assertThat(Nickname.fromDisplayName("홍 길동!")).isEqualTo("홍길동");
		assertThat(Nickname.fromDisplayName("John Smith")).isEqualTo("JohnSmith");
		assertThat(Nickname.fromDisplayName("a".repeat(30))).hasSize(20);
	}

	@Test
	void 쓸_수_있는_글자가_부족하면_대체_닉네임을_만든다() {
		for (String name : new String[] {null, "", "!", "😀😀", "가"}) {
			String nickname = Nickname.fromDisplayName(name);
			assertThat(valid(nickname)).as("name=%s → %s", name, nickname).isTrue();
			assertThat(nickname).startsWith("사용자");
		}
	}
}
