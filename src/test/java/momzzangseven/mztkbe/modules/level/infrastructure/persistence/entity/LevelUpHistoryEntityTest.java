package momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;
import org.junit.jupiter.api.Test;

class LevelUpHistoryEntityTest {

  @Test
  void fromAndToDomain_shouldMapRoundTrip() {
    LevelUpHistory domain =
        LevelUpHistory.reconstitute(
            9L, 1L, 10L, 1, 2, 100, 5, LocalDateTime.of(2026, 2, 28, 10, 0));

    LevelUpHistoryEntity entity = LevelUpHistoryEntity.from(domain);
    LevelUpHistory mapped = entity.toDomain();

    assertThat(mapped.getId()).isEqualTo(9L);
    assertThat(mapped.getUserId()).isEqualTo(1L);
    assertThat(mapped.getToLevel()).isEqualTo(2);
    assertThat(mapped.getSpentXp()).isEqualTo(100);
  }

  @Test
  void onCreate_shouldInitializeCreatedAt() {
    LevelUpHistoryEntity entity =
        LevelUpHistoryEntity.builder()
            .userId(1L)
            .levelPolicyId(10L)
            .fromLevel(1)
            .toLevel(2)
            .spentXp(100)
            .rewardMztk(5)
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
  }
}
