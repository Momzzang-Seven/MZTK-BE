package momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserEntity unit test")
class UserEntityTest {

  @Test
  @DisplayName("onCreate sets timestamps")
  void onCreate_setsTimestamps() {
    UserEntity entity = UserEntity.builder().email("user@example.com").role(UserRole.USER).build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
  }

  @Test
  @DisplayName("onUpdate refreshes updatedAt timestamp")
  void onUpdate_setsCurrentTimestamp() {
    UserEntity entity =
        UserEntity.builder()
            .email("user@example.com")
            .role(UserRole.USER)
            .createdAt(Instant.parse("2026-02-01T10:00:00Z"))
            .updatedAt(Instant.parse("2026-02-01T11:00:00Z"))
            .build();

    Instant before = Instant.now();
    entity.onUpdate();

    assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(before);
  }
}
