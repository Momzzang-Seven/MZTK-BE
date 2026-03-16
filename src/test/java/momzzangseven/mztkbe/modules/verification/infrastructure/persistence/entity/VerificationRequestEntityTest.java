package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import org.junit.jupiter.api.Test;

class VerificationRequestEntityTest {

  @Test
  void mapsDomainToEntityAndBack() {
    VerificationRequest domain =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg");

    VerificationRequestEntity entity = VerificationRequestEntity.from(domain);
    VerificationRequest restored = entity.toDomain();

    assertThat(entity.getTmpObjectKey()).isEqualTo("private/workout/a.jpg");
    assertThat(restored.getVerificationKind()).isEqualTo(VerificationKind.WORKOUT_PHOTO);
  }

  @Test
  void mapsNullableReasonAndFailureFieldsAsNull() {
    VerificationRequest domain =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_RECORD, "private/workout/a.png")
            .toVerified(LocalDate.of(2026, 3, 13), LocalDateTime.of(2026, 3, 13, 9, 0));

    VerificationRequestEntity entity = VerificationRequestEntity.from(domain);
    VerificationRequest restored = entity.toDomain();

    assertThat(entity.getRejectionReasonCode()).isNull();
    assertThat(entity.getFailureCode()).isNull();
    assertThat(restored.getRejectionReasonCode()).isNull();
    assertThat(restored.getFailureCode()).isNull();
  }

  @Test
  void mapsNonNullReasonAndFailureFields() {
    VerificationRequest rejected =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_RECORD, "private/workout/a.png")
            .toRejected(
                RejectionReasonCode.DATE_MISMATCH,
                "exercise date must be today",
                LocalDate.of(2026, 3, 12),
                LocalDateTime.of(2026, 3, 12, 23, 59));
    VerificationRequest failed = rejected.toFailed(FailureCode.EXTERNAL_AI_TIMEOUT);

    VerificationRequestEntity entity = VerificationRequestEntity.from(failed);
    VerificationRequest restored = entity.toDomain();

    assertThat(entity.getFailureCode()).isEqualTo(FailureCode.EXTERNAL_AI_TIMEOUT.name());
    assertThat(restored.getFailureCode()).isEqualTo(FailureCode.EXTERNAL_AI_TIMEOUT);
  }
}
