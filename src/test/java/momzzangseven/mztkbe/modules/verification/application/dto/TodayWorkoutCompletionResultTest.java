package momzzangseven.mztkbe.modules.verification.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;
import org.junit.jupiter.api.Test;

class TodayWorkoutCompletionResultTest {

  @Test
  void createsResult() {
    TodayWorkoutCompletionResult result =
        TodayWorkoutCompletionResult.builder()
            .todayCompleted(true)
            .completedMethod(CompletedMethod.WORKOUT_PHOTO)
            .rewardGrantedToday(true)
            .grantedXp(100)
            .earnedDate(LocalDate.of(2026, 3, 13))
            .build();

    assertThat(result.todayCompleted()).isTrue();
    assertThat(result.completedMethod()).isEqualTo(CompletedMethod.WORKOUT_PHOTO);
  }
}
