package momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;
import org.junit.jupiter.api.Test;

class LevelPolicyEntityTest {

  @Test
  void fromAndToDomain_shouldPreservePolicyFields() {
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
    LocalDateTime to = LocalDateTime.of(2026, 12, 31, 0, 0);
    LevelPolicy domain =
        LevelPolicy.builder()
            .id(1L)
            .level(2)
            .requiredXp(100)
            .rewardMztk(5)
            .effectiveFrom(from)
            .effectiveTo(to)
            .enabled(true)
            .build();

    LevelPolicyEntity entity = LevelPolicyEntity.from(domain);
    LevelPolicy mapped = entity.toDomain();

    assertThat(mapped.getId()).isEqualTo(1L);
    assertThat(mapped.getLevel()).isEqualTo(2);
    assertThat(mapped.getRequiredXp()).isEqualTo(100);
    assertThat(mapped.getRewardMztk()).isEqualTo(5);
    assertThat(mapped.getEffectiveFrom()).isEqualTo(from);
    assertThat(mapped.getEffectiveTo()).isEqualTo(to);
    assertThat(mapped.isEnabled()).isTrue();
  }

  @Test
  void onCreate_shouldFillCreatedAt() {
    LevelPolicyEntity entity =
        LevelPolicyEntity.builder()
            .level(2)
            .requiredXp(100)
            .rewardMztk(5)
            .effectiveFrom(LocalDateTime.of(2026, 1, 1, 0, 0))
            .enabled(true)
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
  }
}
