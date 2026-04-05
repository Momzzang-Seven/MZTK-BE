package momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import momzzangseven.mztkbe.modules.account.domain.model.RefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RefreshTokenEntity unit test")
class RefreshTokenEntityTest {

  @Test
  @DisplayName("updateFrom() updates mutable token fields")
  void updateFrom_updatesMutableTokenFields() {
    Instant now = Instant.now();
    Instant initialExpiresAt = now.plus(Duration.ofDays(1));
    Instant createdAt = now.minus(Duration.ofDays(1));

    RefreshTokenEntity entity =
        RefreshTokenEntity.builder()
            .id(1L)
            .userId(10L)
            .tokenHash("old-hash")
            .expiresAt(initialExpiresAt)
            .createdAt(createdAt)
            .build();

    Instant newExpiresAt = now.plus(Duration.ofDays(2));
    Instant revokedAt = now.minus(Duration.ofHours(1));
    Instant usedAt = now.minus(Duration.ofMinutes(30));
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
    Instant now = Instant.now();
    RefreshTokenEntity entity =
        RefreshTokenEntity.builder()
            .userId(10L)
            .tokenHash("hash")
            .expiresAt(now.plus(Duration.ofDays(1)))
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("onCreate() does not overwrite existing createdAt")
  void onCreate_doesNotOverwriteExistingCreatedAt() {
    Instant now = Instant.now();
    Instant createdAt = now.minus(Duration.ofDays(2));
    RefreshTokenEntity entity =
        RefreshTokenEntity.builder()
            .userId(10L)
            .tokenHash("hash")
            .expiresAt(now.plus(Duration.ofDays(1)))
            .createdAt(createdAt)
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
  }
}
