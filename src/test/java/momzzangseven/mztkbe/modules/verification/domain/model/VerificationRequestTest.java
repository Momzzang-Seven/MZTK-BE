package momzzangseven.mztkbe.modules.verification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerificationRequestTest {

  @Test
  @DisplayName("PENDING -> ANALYZING -> VERIFIED 상태 전이가 가능하다")
  void transitionToVerified() {
    VerificationRequest pending =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg");

    VerificationRequest analyzing = pending.toAnalyzing();
    VerificationRequest verified =
        analyzing.toVerified(LocalDate.of(2026, 3, 13), LocalDateTime.of(2026, 3, 13, 10, 0));

    assertThat(analyzing.getStatus()).isEqualTo(VerificationStatus.ANALYZING);
    assertThat(verified.getStatus()).isEqualTo(VerificationStatus.VERIFIED);
    assertThat(verified.getExerciseDate()).isEqualTo(LocalDate.of(2026, 3, 13));
  }

  @Test
  @DisplayName("FAILED -> ANALYZING 재시도가 가능하다")
  void retryFromFailed() {
    VerificationRequest failed =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_RECORD, "private/workout/a.png")
            .toAnalyzing()
            .toFailed(FailureCode.EXTERNAL_AI_UNAVAILABLE);

    VerificationRequest retried = failed.toAnalyzing();

    assertThat(retried.getStatus()).isEqualTo(VerificationStatus.ANALYZING);
    assertThat(retried.getFailureCode()).isNull();
  }

  @Test
  @DisplayName("REJECTED 상태에는 rejection reason이 저장된다")
  void rejectedStateHasReason() {
    VerificationRequest rejected =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.heic")
            .toAnalyzing()
            .toRejected(
                RejectionReasonCode.EXIF_NOT_TODAY,
                "EXIF shot date must be today in KST",
                LocalDate.of(2026, 3, 12),
                LocalDateTime.of(2026, 3, 12, 23, 59));

    assertThat(rejected.getStatus()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(rejected.getRejectionReasonCode()).isEqualTo(RejectionReasonCode.EXIF_NOT_TODAY);
  }
}
