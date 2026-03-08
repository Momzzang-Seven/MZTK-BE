package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LevelPolicyItemTest {

  @Test
  void builder_shouldCreateExpectedItem() {
    LevelPolicyItem item =
        LevelPolicyItem.builder().currentLevel(5).toLevel(6).requiredXp(500).rewardMztk(20).build();

    assertThat(item.currentLevel()).isEqualTo(5);
    assertThat(item.toLevel()).isEqualTo(6);
    assertThat(item.requiredXp()).isEqualTo(500);
    assertThat(item.rewardMztk()).isEqualTo(20);
  }

  @Test
  void record_shouldUseValueEquality() {
    LevelPolicyItem a = new LevelPolicyItem(1, 2, 100, 3);
    LevelPolicyItem b = new LevelPolicyItem(1, 2, 100, 3);

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }
}
