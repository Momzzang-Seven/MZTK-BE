package momzzangseven.mztkbe.modules.verification.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.Test;

class LatestVerificationItemTest {

  @Test
  void mapsFromVerificationRequest() {
    VerificationRequest request =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_RECORD, "private/workout/a.png")
            .toRejected(
                RejectionReasonCode.DATE_MISMATCH, "exercise date must be today", null, null);

    LatestVerificationItem result = LatestVerificationItem.from(request);

    assertThat(result.verificationId()).isEqualTo(request.getVerificationId());
    assertThat(result.verificationKind()).isEqualTo(VerificationKind.WORKOUT_RECORD);
    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.DATE_MISMATCH);
    assertThat(result.failureCode()).isNull();
  }
}
