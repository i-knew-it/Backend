package com.example.iknewit.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, String> {

	// soft delete된 계정은 제외하고 조회 (계정 복구용 조회는 별도 메서드로)
	boolean existsByEmailAndDeletedAtIsNull(String email);

	Optional<User> findByEmailAndDeletedAtIsNull(String email);

	Optional<User> findByIdAndDeletedAtIsNull(String id);

	// 소셜 계정 조회 (provider + 제공자 측 사용자 ID)
	Optional<User> findByProviderAndProviderIdAndDeletedAtIsNull(AuthProvider provider, String providerId);

	/**
	 * 사용자 행을 잠그고(SELECT ... FOR UPDATE) 조회한다. 같은 사용자에 대한 비밀번호 재설정 작업
	 * (토큰 발급, 토큰으로 재설정)을 한 번에 하나씩 순서대로 처리하기 위한 용도다.
	 * 두 작업 모두 "사용자 행 → 토큰 행" 순서로 잠가야 서로 기다리다 교착(deadlock)에 빠지지 않는다.
	 * 트랜잭션 안에서 호출해야 하고, 트랜잭션이 끝날 때 잠금이 풀린다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.email = :email and u.deletedAt is null")
	Optional<User> findByEmailForUpdate(@Param("email") String email);

	/**
	 * 잠금 없이 비밀번호 해시 문자열만 조회한다. 로그인의 1차 비밀번호 확인용이다.
	 * 엔티티가 아니라 값만 읽으므로 영속성 컨텍스트에 User가 남지 않아, 뒤이은 잠금 조회가 항상 DB의 최신 값을 읽는다.
	 * 계정이 없거나 비밀번호가 없는(소셜 전용) 계정이면 비어 있다.
	 */
	@Query("select u.passwordHash from User u where u.email = :email and u.deletedAt is null")
	Optional<String> findPasswordHashByEmail(@Param("email") String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id and u.deletedAt is null")
	Optional<User> findByIdForUpdate(@Param("id") String id);
}
