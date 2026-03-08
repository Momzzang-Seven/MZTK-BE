package momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserEntity unit test")
class UserEntityTest {

  @Test
  @DisplayName("onCreate sets timestamps and defaults status to ACTIVE")
  void onCreate_withNullStatus_setsDefaults() {
    UserEntity entity =
        UserEntity.builder()
            .provider(AuthProvider.KAKAO)
            .providerUserId("kakao-user")
            .email("kakao@example.com")
            .role(UserRole.USER)
            .status(null)
            .build();

    entity.onCreate();

    assertThat(entity.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
  }

  @Test
  @DisplayName("onCreate keeps explicit status value")
  void onCreate_withExplicitStatus_keepsStatus() {
    UserEntity entity =
        UserEntity.builder()
            .provider(AuthProvider.GOOGLE)
            .providerUserId("google-user")
            .email("google@example.com")
            .role(UserRole.USER)
            .status(UserStatus.DELETED)
            .build();

    entity.onCreate();

    assertThat(entity.getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
  }

  @Test
  @DisplayName("onUpdate refreshes updatedAt timestamp")
  void onUpdate_setsCurrentTimestamp() {
    UserEntity entity =
        UserEntity.builder()
            .provider(AuthProvider.LOCAL)
            .providerUserId("LOCAL:user@example.com")
            .email("user@example.com")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .createdAt(LocalDateTime.of(2026, 2, 1, 10, 0))
            .updatedAt(LocalDateTime.of(2026, 2, 1, 11, 0))
            .build();

    LocalDateTime before = LocalDateTime.now();
    entity.onUpdate();

    assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(before);
  }
}
