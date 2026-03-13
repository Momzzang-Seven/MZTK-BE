package momzzangseven.mztkbe.modules.verification.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.Test;

class VerificationDetailResultTest {

  @Test
  void mapsFromVerificationRequest() {
    VerificationRequest request =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_RECORD, "private/workout/a.png")
            .toRejected(
                RejectionReasonCode.DATE_MISMATCH,
                "exercise date must be today",
                LocalDate.of(2026, 3, 13),
                LocalDateTime.of(2026, 3, 13, 8, 30));

    VerificationDetailResult result = VerificationDetailResult.from(request);

    assertThat(result.verificationId()).isEqualTo(request.getVerificationId());
    assertThat(result.verificationKind()).isEqualTo(VerificationKind.WORKOUT_RECORD);
    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(result.exerciseDate()).isEqualTo(LocalDate.of(2026, 3, 13));
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.DATE_MISMATCH);
    assertThat(result.rejectionReasonDetail()).isEqualTo("exercise date must be today");
    assertThat(result.failureCode()).isNull();
  }
}
