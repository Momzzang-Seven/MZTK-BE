package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.Test;

class XpLedgerEntryItemTest {

  @Test
  void builder_shouldPreserveValues() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 9, 0);

    XpLedgerEntryItem item =
        XpLedgerEntryItem.builder()
            .xpLedgerId(1L)
            .type(XpType.CHECK_IN)
            .xpAmount(10)
            .earnedOn(LocalDate.of(2026, 2, 28))
            .occurredAt(now)
            .idempotencyKey("checkin:1:20260228")
            .sourceRef("attendance")
            .createdAt(now)
            .build();

    assertThat(item.xpLedgerId()).isEqualTo(1L);
    assertThat(item.idempotencyKey()).startsWith("checkin:");
    assertThat(item.createdAt()).isEqualTo(now);
  }
}
