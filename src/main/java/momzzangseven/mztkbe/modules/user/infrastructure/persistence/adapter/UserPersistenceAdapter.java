package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements LoadUserPort, SaveUserPort {

  private final UserJpaRepository userJpaRepository;

  @Override
  public Optional<User> findByProviderAndProviderUserId(
      AuthProvider provider, String providerUserId) {
    return userJpaRepository
        .findByProviderAndProviderUserId(provider, providerUserId)
        .map(this::toDomain);
  }

  @Override
  public Optional<User> findByEmail(String email) {
    return userJpaRepository.findByEmail(email).map(UserEntity::toDomain);
  }

  @Override
  public User save(User user) {
    UserEntity saved = userJpaRepository.save(toEntity(user));
    return toDomain(saved);
  }

  private User toDomain(UserEntity e) {
    return User.builder()
            .id(e.getId())
            .provider(e.getProvider())
            .providerUserId(e.getProviderUserId())
            .email(e.getEmail())
            .passwordHash(e.getPasswordHash())
            .nickname(e.getNickname())
            .role(e.getRole())              // ← 이거 추가
            .profileImageUrl(e.getProfileImageUrl())
            .walletAddress(e.getWalletAddress())
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .build();
  }

  private UserEntity toEntity(User u) {
    return UserEntity.builder()
            .id(u.getId())
            .provider(u.getProvider())
            .providerUserId(u.getProviderUserId())
            .email(u.getEmail())
            .passwordHash(u.getPasswordHash())
            .nickname(u.getNickname())
            .role(u.getRole())              // ← 이것도 추가
            .profileImageUrl(u.getProfileImageUrl())
            .walletAddress(u.getWalletAddress())
            .createdAt(u.getCreatedAt())
            .updatedAt(u.getUpdatedAt())
            .build();
  }
}
