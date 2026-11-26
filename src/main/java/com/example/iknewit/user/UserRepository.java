package com.example.iknewit.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

	// soft delete된 계정은 제외하고 조회 (계정 복구용 조회는 별도 메서드로)
	boolean existsByEmailAndDeletedAtIsNull(String email);

	Optional<User> findByEmailAndDeletedAtIsNull(String email);

	Optional<User> findByIdAndDeletedAtIsNull(String id);

	// 소셜 계정 조회 (provider + 제공자 측 사용자 ID)
	Optional<User> findByProviderAndProviderIdAndDeletedAtIsNull(AuthProvider provider, String providerId);
}
