package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.user.application.port.out.DeleteUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persistence adapter implementing user port interfaces using UserJpaRepository. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements LoadUserPort, SaveUserPort, DeleteUserPort {

  private final UserJpaRepository userJpaRepository;

  // ========== LoadUserPort Implementation ==========

  @Override
  @Transactional(readOnly = true)
  public Optional<User> loadUserByEmail(String email) {
    log.debug("Loading user by email: {}", email);
    return userJpaRepository.findByEmail(email).map(this::mapToDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> loadUserById(Long userId) {
    log.debug("Loading user by id: {}", userId);
    return userJpaRepository.findById(userId).map(this::mapToDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<User> loadUsersByIds(Collection<Long> userIds) {
    return userJpaRepository.findAllById(userIds).stream().map(this::mapToDomain).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByEmail(String email) {
    log.debug("Checking if email exists: {}", email);
    return userJpaRepository.existsByEmail(email);
  }

  // ========== SaveUserPort Implementation ==========

  @Override
  @Transactional
  public User saveUser(User user) {
    log.debug("Saving user: {}", user.getEmail());

    UserEntity entity;

    if (user.getId() != null) {
      entity =
          userJpaRepository
              .findById(user.getId())
              .orElseThrow(
                  () -> new IllegalArgumentException("User not found with ID: " + user.getId()));

      updateEntityFromDomain(entity, user);
    } else {
      entity = mapToEntity(user);
    }

    UserEntity savedEntity = userJpaRepository.save(entity);
    log.debug("User saved with ID: {}", savedEntity.getId());

    return mapToDomain(savedEntity);
  }

  @Override
  @Transactional
  public void deleteAllByIdInBatch(List<Long> userIds) {
    userJpaRepository.deleteAllByIdInBatch(userIds);
  }

  // ========== Mapping Methods (Translator Pattern) ==========

  /** Convert UserEntity (Infrastructure) to User (Domain). */
  private User mapToDomain(UserEntity entity) {
    return User.builder()
        .id(entity.getId())
        .email(entity.getEmail())
        .nickname(entity.getNickname())
        .profileImageUrl(entity.getProfileImageUrl())
        .role(entity.getRole())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /** Convert User (Domain) to UserEntity (Infrastructure). Used for creating new entities. */
  private UserEntity mapToEntity(User user) {
    return UserEntity.builder()
        .id(user.getId())
        .email(user.getEmail())
        .nickname(user.getNickname())
        .profileImageUrl(user.getProfileImageUrl())
        .role(user.getRole())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .build();
  }

  private void updateEntityFromDomain(UserEntity entity, User user) {
    entity.setEmail(user.getEmail());
    entity.setNickname(user.getNickname());
    entity.setProfileImageUrl(user.getProfileImageUrl());
    entity.setRole(user.getRole());
    entity.setUpdatedAt(user.getUpdatedAt());
  }
}
