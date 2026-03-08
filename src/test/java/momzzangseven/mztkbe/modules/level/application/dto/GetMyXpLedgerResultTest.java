package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.Test;

class GetMyXpLedgerResultTest {

  @Test
  void builder_shouldSetEntriesAndCaps() {
    XpLedgerEntryItem entry =
        XpLedgerEntryItem.builder()
            .xpLedgerId(1L)
            .type(XpType.CHECK_IN)
            .xpAmount(10)
            .earnedOn(LocalDate.of(2026, 2, 28))
            .occurredAt(LocalDateTime.of(2026, 2, 28, 9, 0))
            .idempotencyKey("checkin:1:20260228")
            .sourceRef("attendance")
            .createdAt(LocalDateTime.of(2026, 2, 28, 9, 0))
            .build();

    XpDailyCapStatusItem cap =
        XpDailyCapStatusItem.builder()
            .type(XpType.CHECK_IN)
            .dailyCap(1)
            .grantedCount(0)
            .remainingCount(1)
            .build();

    GetMyXpLedgerResult result =
        GetMyXpLedgerResult.builder()
            .page(0)
            .size(10)
            .hasNext(false)
            .earnedOn(LocalDate.of(2026, 2, 28))
            .entries(List.of(entry))
            .todayCaps(List.of(cap))
            .build();

    assertThat(result.entries()).containsExactly(entry);
    assertThat(result.todayCaps()).containsExactly(cap);
    assertThat(result.hasNext()).isFalse();
  }
}
