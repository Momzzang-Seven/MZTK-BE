package momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.Test;

class XpLedgerEntityTest {

  @Test
  void fromAndToDomain_shouldMapAllFields() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 9, 0);
    XpLedgerEntry domain =
        XpLedgerEntry.builder()
            .id(1L)
            .userId(2L)
            .type(XpType.WORKOUT)
            .xpAmount(30)
            .earnedOn(LocalDate.of(2026, 2, 28))
            .occurredAt(now)
            .idempotencyKey("workout:2:20260228")
            .sourceRef("location-verification:10")
            .createdAt(now)
            .build();

    XpLedgerEntity entity = XpLedgerEntity.from(domain);
    XpLedgerEntry mapped = entity.toDomain();

    assertThat(mapped.getId()).isEqualTo(1L);
    assertThat(mapped.getType()).isEqualTo(XpType.WORKOUT);
    assertThat(mapped.getXpAmount()).isEqualTo(30);
    assertThat(mapped.getIdempotencyKey()).isEqualTo("workout:2:20260228");
  }

  @Test
  void onCreate_shouldSetCreatedAtIfMissing() {
    XpLedgerEntity entity =
        XpLedgerEntity.builder()
            .userId(2L)
            .type(XpType.WORKOUT)
            .xpAmount(30)
            .earnedOn(LocalDate.of(2026, 2, 28))
            .occurredAt(LocalDateTime.of(2026, 2, 28, 9, 0))
            .idempotencyKey("workout:2:20260228")
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
  }
}
