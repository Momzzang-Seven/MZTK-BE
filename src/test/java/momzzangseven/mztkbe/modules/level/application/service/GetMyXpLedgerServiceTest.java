package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyXpLedgerResult;
import momzzangseven.mztkbe.modules.level.application.port.out.PolicyPort;
import momzzangseven.mztkbe.modules.level.application.port.out.XpLedgerPort;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;
import momzzangseven.mztkbe.modules.level.domain.model.XpPolicy;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetMyXpLedgerServiceTest {

  @Mock private XpLedgerPort xpLedgerPort;
  @Mock private PolicyPort policyPort;

  private GetMyXpLedgerService service;

  @BeforeEach
  void setUp() {
    service = new GetMyXpLedgerService(xpLedgerPort, policyPort, ZoneId.of("Asia/Seoul"));
  }

  @Test
  void execute_shouldThrowWhenUserIdIsNull() {
    assertThatThrownBy(() -> service.execute(null, 0, 10))
        .isInstanceOf(UserNotAuthenticatedException.class);
  }

  @Test
  void execute_shouldThrowWhenPageOrSizeInvalid() {
    assertThatThrownBy(() -> service.execute(1L, -1, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("page must be >= 0");
    assertThatThrownBy(() -> service.execute(1L, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("size must be between");
  }

  @Test
  void execute_shouldTrimToRequestedSizeAndSetHasNext() {
    XpLedgerEntry first = ledger(1L, XpType.CHECK_IN, 10, "checkin:1:20260226");
    XpLedgerEntry second = ledger(2L, XpType.POST, 20, "post:1:20260226");

    when(xpLedgerPort.loadXpLedgerEntries(1L, 0, 1)).thenReturn(List.of(first, second));
    when(policyPort.loadXpPolicies(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(xpPolicy(XpType.CHECK_IN, 10, 3)));
    when(xpLedgerPort.countByUserIdAndTypeAndEarnedOn(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq(XpType.CHECK_IN),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(1);

    GetMyXpLedgerResult result = service.execute(1L, 0, 1);

    assertThat(result.hasNext()).isTrue();
    assertThat(result.entries()).hasSize(1);
    assertThat(result.entries().getFirst().idempotencyKey()).isEqualTo("checkin:1:20260226");
  }

  @Test
  void execute_shouldComputeRemainingCountWithUnlimitedAndClampToZero() {
    when(xpLedgerPort.loadXpLedgerEntries(1L, 0, 10)).thenReturn(List.of());
    when(policyPort.loadXpPolicies(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(xpPolicy(XpType.CHECK_IN, 10, -1), xpPolicy(XpType.POST, 20, 2)));

    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    when(xpLedgerPort.countByUserIdAndTypeAndEarnedOn(1L, XpType.CHECK_IN, today)).thenReturn(99);
    when(xpLedgerPort.countByUserIdAndTypeAndEarnedOn(1L, XpType.POST, today)).thenReturn(5);

    GetMyXpLedgerResult result = service.execute(1L, 0, 10);

    assertThat(result.todayCaps()).hasSize(2);
    assertThat(result.todayCaps().getFirst().type()).isEqualTo(XpType.CHECK_IN);
    assertThat(result.todayCaps().getFirst().remainingCount()).isEqualTo(-1);
    assertThat(result.todayCaps().get(1).type()).isEqualTo(XpType.POST);
    assertThat(result.todayCaps().get(1).remainingCount()).isZero();
  }

  private XpPolicy xpPolicy(XpType type, int xpAmount, int dailyCap) {
    return XpPolicy.builder()
        .id(1L)
        .type(type)
        .xpAmount(xpAmount)
        .dailyCap(dailyCap)
        .enabled(true)
        .build();
  }

  private XpLedgerEntry ledger(Long id, XpType type, int amount, String key) {
    return XpLedgerEntry.builder()
        .id(id)
        .userId(1L)
        .type(type)
        .xpAmount(amount)
        .earnedOn(LocalDate.of(2026, 2, 26))
        .occurredAt(LocalDateTime.of(2026, 2, 26, 9, 0))
        .idempotencyKey(key)
        .sourceRef("src")
        .createdAt(LocalDateTime.of(2026, 2, 26, 9, 0))
        .build();
  }
}
