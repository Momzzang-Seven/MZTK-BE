package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.level.application.port.out.XpLedgerPort;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetTodayWorkoutRewardServiceTest {

  @Mock private XpLedgerPort xpLedgerPort;

  private GetTodayWorkoutRewardService service;

  @BeforeEach
  void setUp() {
    service = new GetTodayWorkoutRewardService(xpLedgerPort);
  }

  @Test
  void returnsRewardSnapshotWhenWorkoutLedgerExists() {
    LocalDate earnedOn = LocalDate.of(2026, 3, 13);
    XpLedgerEntry entry =
        XpLedgerEntry.builder()
            .id(1L)
            .userId(7L)
            .type(XpType.WORKOUT)
            .xpAmount(100)
            .earnedOn(earnedOn)
            .occurredAt(LocalDateTime.of(2026, 3, 13, 10, 0))
            .idempotencyKey("workout:123")
            .sourceRef("workout-photo-verification:123")
            .createdAt(LocalDateTime.of(2026, 3, 13, 10, 0))
            .build();
    when(xpLedgerPort.findLatestByUserIdAndTypeAndEarnedOn(7L, XpType.WORKOUT, earnedOn))
        .thenReturn(Optional.of(entry));

    var result = service.execute(7L, earnedOn);

    assertThat(result.rewarded()).isTrue();
    assertThat(result.grantedXp()).isEqualTo(100);
    assertThat(result.sourceRef()).isEqualTo("workout-photo-verification:123");
  }

  @Test
  void returnsNoneWhenWorkoutLedgerDoesNotExist() {
    LocalDate earnedOn = LocalDate.of(2026, 3, 13);
    when(xpLedgerPort.findLatestByUserIdAndTypeAndEarnedOn(7L, XpType.WORKOUT, earnedOn))
        .thenReturn(Optional.empty());

    var result = service.execute(7L, earnedOn);

    assertThat(result.rewarded()).isFalse();
    assertThat(result.grantedXp()).isZero();
    assertThat(result.earnedDate()).isEqualTo(earnedOn);
  }
}
