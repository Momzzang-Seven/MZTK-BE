package momzzangseven.mztkbe.modules.verification.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.Test;

class TodayWorkoutCompletionResultTest {

  @Test
  void createsResultFromRewardSnapshot() {
    LatestVerificationItem latestVerification =
        LatestVerificationItem.builder()
            .verificationId("verification-1")
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .verificationStatus(VerificationStatus.VERIFIED)
            .build();
    TodayWorkoutCompletionResult result =
        TodayWorkoutCompletionResult.from(
            new TodayRewardSnapshot(
                true, 100, LocalDate.of(2026, 3, 13), "workout-photo-verification:verification-1"),
            CompletedMethod.WORKOUT_PHOTO,
            latestVerification);

    assertThat(result.todayCompleted()).isTrue();
    assertThat(result.completedMethod()).isEqualTo(CompletedMethod.WORKOUT_PHOTO);
    assertThat(result.rewardGrantedToday()).isTrue();
    assertThat(result.grantedXp()).isEqualTo(100);
    assertThat(result.earnedDate()).isEqualTo(LocalDate.of(2026, 3, 13));
    assertThat(result.latestVerification()).isEqualTo(latestVerification);
  }

  @Test
  void createsIncompleteResultFromUnrewardedSnapshot() {
    TodayWorkoutCompletionResult result =
        TodayWorkoutCompletionResult.from(
            TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)), null, null);

    assertThat(result.todayCompleted()).isFalse();
    assertThat(result.completedMethod()).isNull();
    assertThat(result.rewardGrantedToday()).isFalse();
    assertThat(result.grantedXp()).isZero();
    assertThat(result.earnedDate()).isEqualTo(LocalDate.of(2026, 3, 13));
    assertThat(result.latestVerification()).isNull();
  }
}
