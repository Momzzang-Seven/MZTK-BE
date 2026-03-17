package momzzangseven.mztkbe.modules.verification.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import org.junit.jupiter.api.Test;

class VerificationEvaluationResultTest {

  @Test
  void verifiedFactoryMarksOnlyVerifiedState() {
    VerificationEvaluationResult result =
        VerificationEvaluationResult.verified(
            LocalDate.of(2026, 3, 13), LocalDateTime.of(2026, 3, 13, 9, 0));

    assertThat(result.verified()).isTrue();
    assertThat(result.rejected()).isFalse();
    assertThat(result.failed()).isFalse();
  }

  @Test
  void rejectedFactoryMarksOnlyRejectedState() {
    VerificationEvaluationResult result =
        VerificationEvaluationResult.rejected(
            RejectionReasonCode.DATE_MISMATCH,
            "exercise date must be today",
            LocalDate.of(2026, 3, 12),
            LocalDateTime.of(2026, 3, 12, 23, 59));

    assertThat(result.verified()).isFalse();
    assertThat(result.rejected()).isTrue();
    assertThat(result.failed()).isFalse();
  }

  @Test
  void failedFactoryMarksOnlyFailedState() {
    VerificationEvaluationResult result =
        VerificationEvaluationResult.failed(FailureCode.EXTERNAL_AI_TIMEOUT);

    assertThat(result.verified()).isFalse();
    assertThat(result.rejected()).isFalse();
    assertThat(result.failed()).isTrue();
  }
}
