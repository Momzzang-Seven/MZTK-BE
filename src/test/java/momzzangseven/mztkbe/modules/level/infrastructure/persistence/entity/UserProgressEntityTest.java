package momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;
import org.junit.jupiter.api.Test;

class UserProgressEntityTest {

  @Test
  void fromAndToDomain_shouldMapProgressValues() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 10, 0);
    UserProgress domain =
        UserProgress.builder()
            .userId(1L)
            .level(3)
            .availableXp(200)
            .lifetimeXp(400)
            .createdAt(now.minusDays(1))
            .updatedAt(now)
            .build();

    UserProgressEntity entity = UserProgressEntity.from(domain);
    UserProgress mapped = entity.toDomain();

    assertThat(mapped.getUserId()).isEqualTo(1L);
    assertThat(mapped.getLevel()).isEqualTo(3);
    assertThat(mapped.getAvailableXp()).isEqualTo(200);
    assertThat(mapped.getLifetimeXp()).isEqualTo(400);
  }

  @Test
  void lifecycleHooks_shouldSetTimestamps() {
    UserProgressEntity entity =
        UserProgressEntity.builder().userId(1L).level(1).availableXp(0).lifetimeXp(0).build();

    entity.onCreate();
    LocalDateTime created = entity.getCreatedAt();
    LocalDateTime beforeUpdate = entity.getUpdatedAt();
    entity.onUpdate();

    assertThat(created).isNotNull();
    assertThat(entity.getUpdatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(beforeUpdate);
  }
}
