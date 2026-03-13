package momzzangseven.mztkbe.modules.verification.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import momzzangseven.mztkbe.modules.verification.application.dto.LatestVerificationItem;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayWorkoutCompletionResult;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.Test;

class TodayWorkoutCompletionResponseDTOTest {

  @Test
  void mapsLatestVerificationWhenPresent() {
    TodayWorkoutCompletionResult result =
        TodayWorkoutCompletionResult.builder()
            .todayCompleted(true)
            .completedMethod(CompletedMethod.WORKOUT_PHOTO)
            .rewardGrantedToday(true)
            .grantedXp(100)
            .earnedDate(LocalDate.of(2026, 3, 13))
            .latestVerification(
                LatestVerificationItem.builder()
                    .verificationId("verification-1")
                    .verificationKind(VerificationKind.WORKOUT_PHOTO)
                    .verificationStatus(VerificationStatus.VERIFIED)
                    .build())
            .build();

    TodayWorkoutCompletionResponseDTO dto = TodayWorkoutCompletionResponseDTO.from(result);

    assertThat(dto.latestVerification()).isNotNull();
    assertThat(dto.latestVerification().verificationId()).isEqualTo("verification-1");
  }

  @Test
  void mapsLatestVerificationAsNullWhenAbsent() {
    TodayWorkoutCompletionResult result =
        TodayWorkoutCompletionResult.builder()
            .todayCompleted(false)
            .completedMethod(null)
            .rewardGrantedToday(false)
            .grantedXp(0)
            .earnedDate(LocalDate.of(2026, 3, 13))
            .latestVerification(null)
            .build();

    TodayWorkoutCompletionResponseDTO dto = TodayWorkoutCompletionResponseDTO.from(result);

    assertThat(dto.latestVerification()).isNull();
  }
}
