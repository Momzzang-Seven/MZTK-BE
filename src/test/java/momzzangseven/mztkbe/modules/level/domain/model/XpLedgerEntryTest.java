package momzzangseven.mztkbe.modules.level.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.Test;

class XpLedgerEntryTest {

  @Test
  void create_shouldThrowWhenUserIdInvalid() {
    assertThatThrownBy(
            () ->
                XpLedgerEntry.create(
                    0L,
                    XpType.CHECK_IN,
                    10,
                    LocalDate.of(2026, 2, 26),
                    LocalDateTime.of(2026, 2, 26, 9, 0),
                    "checkin:1:20260226",
                    "attendance:2026-02-26"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("userId must be positive");

    assertThatThrownBy(
            () ->
                XpLedgerEntry.create(
                    null,
                    XpType.CHECK_IN,
                    10,
                    LocalDate.of(2026, 2, 26),
                    LocalDateTime.of(2026, 2, 26, 9, 0),
                    "checkin:1:20260226",
                    "attendance:2026-02-26"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("userId must be positive");
  }

  @Test
  void create_shouldThrowWhenTypeNull() {
    assertThatThrownBy(
            () ->
                XpLedgerEntry.create(
                    1L,
                    null,
                    10,
                    LocalDate.of(2026, 2, 26),
                    LocalDateTime.of(2026, 2, 26, 9, 0),
                    "checkin:1:20260226",
                    "attendance:2026-02-26"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("type is required");
  }

  @Test
  void create_shouldThrowWhenXpAmountIsNotPositive() {
    assertThatThrownBy(
            () ->
                XpLedgerEntry.create(
                    1L,
                    XpType.CHECK_IN,
                    0,
                    LocalDate.of(2026, 2, 26),
                    LocalDateTime.of(2026, 2, 26, 9, 0),
                    "checkin:1:20260226",
                    "attendance:2026-02-26"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("xpAmount must be positive");
  }

  @Test
  void create_shouldThrowWhenIdempotencyKeyIsBlank() {
    assertThatThrownBy(
            () ->
                XpLedgerEntry.create(
                    1L,
                    XpType.CHECK_IN,
                    10,
                    LocalDate.of(2026, 2, 26),
                    LocalDateTime.of(2026, 2, 26, 9, 0),
                    " ",
                    "attendance:2026-02-26"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("idempotencyKey is required");
  }

  @Test
  void create_shouldThrowWhenIdempotencyKeyIsNull() {
    assertThatThrownBy(
            () ->
                XpLedgerEntry.create(
                    1L,
                    XpType.CHECK_IN,
                    10,
                    LocalDate.of(2026, 2, 26),
                    LocalDateTime.of(2026, 2, 26, 9, 0),
                    null,
                    "attendance:2026-02-26"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("idempotencyKey is required");
  }

  @Test
  void create_shouldThrowWhenEarnedOnNull() {
    assertThatThrownBy(
            () ->
                XpLedgerEntry.create(
                    1L,
                    XpType.CHECK_IN,
                    10,
                    null,
                    LocalDateTime.of(2026, 2, 26, 9, 0),
                    "checkin:1:20260226",
                    "attendance:2026-02-26"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("earnedOn is required");
  }

  @Test
  void create_shouldThrowWhenOccurredAtNull() {
    assertThatThrownBy(
            () ->
                XpLedgerEntry.create(
                    1L,
                    XpType.CHECK_IN,
                    10,
                    LocalDate.of(2026, 2, 26),
                    null,
                    "checkin:1:20260226",
                    "attendance:2026-02-26"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("occurredAt is required");
  }

  @Test
  void create_shouldCreateEntryWhenInputIsValid() {
    XpLedgerEntry entry =
        XpLedgerEntry.create(
            1L,
            XpType.CHECK_IN,
            10,
            LocalDate.of(2026, 2, 26),
            LocalDateTime.of(2026, 2, 26, 9, 0),
            "checkin:1:20260226",
            "attendance:2026-02-26");

    assertThat(entry.getUserId()).isEqualTo(1L);
    assertThat(entry.getType()).isEqualTo(XpType.CHECK_IN);
    assertThat(entry.getXpAmount()).isEqualTo(10);
    assertThat(entry.getCreatedAt()).isNotNull();
  }
}
