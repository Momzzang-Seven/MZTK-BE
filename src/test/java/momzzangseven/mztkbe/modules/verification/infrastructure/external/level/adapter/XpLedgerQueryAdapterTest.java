package momzzangseven.mztkbe.modules.verification.infrastructure.external.level.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import momzzangseven.mztkbe.modules.level.application.dto.GetTodayWorkoutRewardResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GetTodayWorkoutRewardUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class XpLedgerQueryAdapterTest {

  @Mock private GetTodayWorkoutRewardUseCase getTodayWorkoutRewardUseCase;

  private XpLedgerQueryAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new XpLedgerQueryAdapter(getTodayWorkoutRewardUseCase);
  }

  @Test
  void mapsRewardResultToVerificationSnapshot() {
    LocalDate earnedOn = LocalDate.of(2026, 3, 13);
    when(getTodayWorkoutRewardUseCase.execute(9L, earnedOn))
        .thenReturn(
            new GetTodayWorkoutRewardResult(
                true, 100, earnedOn, "workout-record-verification:verification-1"));

    var result = adapter.findTodayWorkoutReward(9L, earnedOn);

    assertThat(result.rewarded()).isTrue();
    assertThat(result.grantedXp()).isEqualTo(100);
    assertThat(result.sourceRef()).isEqualTo("workout-record-verification:verification-1");
  }
}
