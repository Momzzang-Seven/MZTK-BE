package momzzangseven.mztkbe.modules.verification.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FailureCodeTest {

  @Test
  void containsContractCodes() {
    assertThat(FailureCode.values())
        .contains(
            FailureCode.EXTERNAL_AI_TIMEOUT,
            FailureCode.EXTERNAL_AI_UNAVAILABLE,
            FailureCode.EXTERNAL_AI_MALFORMED_RESPONSE,
            FailureCode.AI_RESPONSE_SCHEMA_INVALID,
            FailureCode.ORIGINAL_IMAGE_READ_FAILED,
            FailureCode.IMAGE_DECODE_FAILED,
            FailureCode.ANALYSIS_IMAGE_GENERATION_FAILED);
  }
}
