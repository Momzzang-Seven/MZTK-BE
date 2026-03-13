package momzzangseven.mztkbe.modules.verification.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerificationKindTest {

  @Test
  void hasExpectedKinds() {
    assertThat(VerificationKind.values())
        .containsExactly(VerificationKind.WORKOUT_PHOTO, VerificationKind.WORKOUT_RECORD);
  }
}
