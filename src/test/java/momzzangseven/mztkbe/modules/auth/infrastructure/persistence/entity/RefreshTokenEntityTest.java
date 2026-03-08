package momzzangseven.mztkbe.modules.auth.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RefreshTokenEntity unit test")
class RefreshTokenEntityTest {

  @Test
  @DisplayName("updateFrom() updates mutable token fields")
  void updateFrom_updatesMutableTokenFields() {
    LocalDateTime initialExpiresAt = LocalDateTime.now().plusDays(1);
    LocalDateTime createdAt = LocalDateTime.now().minusDays(1);

    RefreshTokenEntity entity =
        RefreshTokenEntity.builder()
            .id(1L)
            .userId(10L)
            .tokenHash("old-hash")
            .expiresAt(initialExpiresAt)
            .createdAt(createdAt)
            .build();

    LocalDateTime newExpiresAt = LocalDateTime.now().plusDays(2);
    LocalDateTime revokedAt = LocalDateTime.now().minusHours(1);
    LocalDateTime usedAt = LocalDateTime.now().minusMinutes(30);
    RefreshToken token =
        RefreshToken.builder()
            .userId(10L)
            .tokenValue("refresh-token-value")
            .expiresAt(newExpiresAt)
            .revokedAt(revokedAt)
            .usedAt(usedAt)
            .createdAt(createdAt)
            .build();

    entity.updateFrom(token, "new-hash");

    assertThat(entity.getUserId()).isEqualTo(10L);
    assertThat(entity.getTokenHash()).isEqualTo("new-hash");
    assertThat(entity.getExpiresAt()).isEqualTo(newExpiresAt);
    assertThat(entity.getRevokedAt()).isEqualTo(revokedAt);
    assertThat(entity.getUsedAt()).isEqualTo(usedAt);
  }

  @Test
  @DisplayName("onCreate() sets createdAt when missing")
  void onCreate_setsCreatedAtWhenMissing() {
    RefreshTokenEntity entity =
        RefreshTokenEntity.builder()
            .userId(10L)
            .tokenHash("hash")
            .expiresAt(LocalDateTime.now().plusDays(1))
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("onCreate() does not overwrite existing createdAt")
  void onCreate_doesNotOverwriteExistingCreatedAt() {
    LocalDateTime createdAt = LocalDateTime.now().minusDays(2);
    RefreshTokenEntity entity =
        RefreshTokenEntity.builder()
            .userId(10L)
            .tokenHash("hash")
            .expiresAt(LocalDateTime.now().plusDays(1))
            .createdAt(createdAt)
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
  }
}
