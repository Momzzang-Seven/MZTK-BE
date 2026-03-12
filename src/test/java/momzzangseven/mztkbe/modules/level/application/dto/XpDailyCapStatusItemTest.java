package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.Test;

class XpDailyCapStatusItemTest {

  @Test
  void builder_shouldCreateExpectedStatusItem() {
    XpDailyCapStatusItem item =
        XpDailyCapStatusItem.builder()
            .type(XpType.POST)
            .dailyCap(3)
            .grantedCount(2)
            .remainingCount(1)
            .build();

    assertThat(item.type()).isEqualTo(XpType.POST);
    assertThat(item.remainingCount()).isEqualTo(1);
  }
}
