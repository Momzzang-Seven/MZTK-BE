package momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository;

import java.util.Optional;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByProviderAndProviderUserId(
      AuthProvider provider, String providerUserId);

  Optional<UserEntity> findByWalletAddress(String walletAddress);

  Optional<UserEntity> findByEmail(String email);
}
