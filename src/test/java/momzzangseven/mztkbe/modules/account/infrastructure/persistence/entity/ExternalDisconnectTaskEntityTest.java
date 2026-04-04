package momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExternalDisconnectTaskEntity unit test")
class ExternalDisconnectTaskEntityTest {

  @Test
  @DisplayName("onCreate initializes timestamps and default status")
  void onCreate_withNullFields_setsDefaults() {
    ExternalDisconnectTaskEntity entity =
        ExternalDisconnectTaskEntity.builder()
            .userId(1L)
            .provider(AuthProvider.KAKAO)
            .providerUserId("kakao-user")
            .attemptCount(1)
            .status(null)
            .createdAt(null)
            .updatedAt(null)
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isNotNull();
    assertThat(entity.getStatus()).isEqualTo(ExternalDisconnectStatus.PENDING);
  }

  @Test
  @DisplayName("onCreate preserves explicitly provided status and timestamps")
  void onCreate_withPresetFields_keepsValues() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 2, 1, 10, 0);
    LocalDateTime updatedAt = LocalDateTime.of(2026, 2, 2, 10, 0);
    ExternalDisconnectTaskEntity entity =
        ExternalDisconnectTaskEntity.builder()
            .userId(1L)
            .provider(AuthProvider.GOOGLE)
            .providerUserId("google-user")
            .attemptCount(2)
            .status(ExternalDisconnectStatus.FAILED)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    assertThat(entity.getStatus()).isEqualTo(ExternalDisconnectStatus.FAILED);
  }

  @Test
  @DisplayName("onUpdate refreshes updatedAt")
  void onUpdate_setsCurrentUpdatedAt() {
    ExternalDisconnectTaskEntity entity =
        ExternalDisconnectTaskEntity.builder()
            .userId(1L)
            .provider(AuthProvider.KAKAO)
            .providerUserId("kakao-user")
            .attemptCount(1)
            .status(ExternalDisconnectStatus.PENDING)
            .createdAt(LocalDateTime.of(2026, 2, 1, 10, 0))
            .updatedAt(LocalDateTime.of(2026, 2, 1, 11, 0))
            .build();

    LocalDateTime before = LocalDateTime.now();
    entity.onUpdate();

    assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(before);
  }
}
