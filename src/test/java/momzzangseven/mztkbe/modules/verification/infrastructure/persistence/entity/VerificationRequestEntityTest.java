package momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
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
}
