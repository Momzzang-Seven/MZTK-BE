package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.Test;

class XpPolicyItemTest {

  @Test
  void builder_shouldCreateItem() {
    XpPolicyItem item =
        XpPolicyItem.builder().type(XpType.WORKOUT).xpAmount(30).dailyCap(2).build();

    assertThat(item.type()).isEqualTo(XpType.WORKOUT);
    assertThat(item.xpAmount()).isEqualTo(30);
    assertThat(item.dailyCap()).isEqualTo(2);
  }

  @Test
  void record_shouldUseValueEquality() {
    XpPolicyItem a = new XpPolicyItem(XpType.WORKOUT, 30, 2);
    XpPolicyItem b = new XpPolicyItem(XpType.WORKOUT, 30, 2);

    assertThat(a).isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }
}
