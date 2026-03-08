package momzzangseven.mztkbe.modules.level.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LevelPolicyTest {

  @Test
  void builder_shouldExposeConfiguredState() {
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
    LocalDateTime to = LocalDateTime.of(2026, 12, 31, 23, 59);

    LevelPolicy policy =
        LevelPolicy.builder()
            .id(1L)
            .level(3)
            .requiredXp(300)
            .rewardMztk(15)
            .effectiveFrom(from)
            .effectiveTo(to)
            .enabled(true)
            .build();

    assertThat(policy.getLevel()).isEqualTo(3);
    assertThat(policy.getRequiredXp()).isEqualTo(300);
    assertThat(policy.getRewardMztk()).isEqualTo(15);
    assertThat(policy.getEffectiveFrom()).isEqualTo(from);
    assertThat(policy.getEffectiveTo()).isEqualTo(to);
    assertThat(policy.isEnabled()).isTrue();
  }

  @Test
  void toBuilder_shouldSupportPolicyUpdateTransition() {
    LevelPolicy original =
        LevelPolicy.builder().id(1L).level(3).requiredXp(300).rewardMztk(15).enabled(true).build();

    LevelPolicy updated = original.toBuilder().requiredXp(350).build();

    assertThat(updated.getRequiredXp()).isEqualTo(350);
    assertThat(updated.getLevel()).isEqualTo(original.getLevel());
    assertThat(original.getRequiredXp()).isEqualTo(300);
  }
}
