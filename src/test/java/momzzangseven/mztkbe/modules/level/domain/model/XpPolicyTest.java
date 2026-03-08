package momzzangseven.mztkbe.modules.level.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.Test;

class XpPolicyTest {

  @Test
  void isUnlimited_shouldReturnTrueForMinusOne() {
    XpPolicy policy =
        XpPolicy.builder()
            .id(1L)
            .type(XpType.CHECK_IN)
            .xpAmount(10)
            .dailyCap(-1)
            .enabled(true)
            .build();

    assertThat(policy.isUnlimited()).isTrue();
  }

  @Test
  void isUnlimited_shouldReturnFalseForNonUnlimitedCap() {
    XpPolicy policy =
        XpPolicy.builder()
            .id(1L)
            .type(XpType.CHECK_IN)
            .xpAmount(10)
            .dailyCap(0)
            .enabled(true)
            .build();

    assertThat(policy.isUnlimited()).isFalse();
  }
}
