package momzzangseven.mztkbe.modules.verification.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RejectionReasonCodeTest {

  @Test
  void containsContractCodes() {
    assertThat(RejectionReasonCode.values())
        .contains(
            RejectionReasonCode.SCREEN_OR_UI,
            RejectionReasonCode.NO_PERSON_VISIBLE,
            RejectionReasonCode.EQUIPMENT_ONLY,
            RejectionReasonCode.INSUFFICIENT_WORKOUT_CONTEXT,
            RejectionReasonCode.LOW_CONFIDENCE,
            RejectionReasonCode.NOT_EXERCISE_PHOTO,
            RejectionReasonCode.NOT_WORKOUT_RECORD,
            RejectionReasonCode.DATE_NOT_VISIBLE,
            RejectionReasonCode.MISSING_OR_INVALID_DATE,
            RejectionReasonCode.NOT_TODAY_EXERCISE,
            RejectionReasonCode.EXIF_MISSING,
            RejectionReasonCode.EXIF_NOT_TODAY);
  }
}
