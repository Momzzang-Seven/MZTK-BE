package momzzangseven.mztkbe.modules.image.application.dto;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** GetImagesByReferenceCommand.validate() 단위 테스트. */
@DisplayName("GetImagesByReferenceCommand validate() 단위 테스트")
class GetImagesByReferenceCommandTest {

  private static final ImageReferenceType FREE = ImageReferenceType.COMMUNITY_FREE;

  @Nested
  @DisplayName("성공 케이스 — validate() 통과")
  class SuccessCases {

    @Test
    @DisplayName("[V-OK] 올바른 referenceType + 양수 referenceId → 예외 없음")
    void validate_validInputs_passes() {
      assertThatNoException()
          .isThrownBy(() -> new GetImagesByReferenceCommand(FREE, 1L).validate());
    }
  }

  @Nested
  @DisplayName("실패 케이스 — referenceType 검증")
  class ReferenceTypeValidationFailures {

    @Test
    @DisplayName("[TC-IMAGE-DOMAIN-012] referenceType=null → IllegalArgumentException")
    void validate_nullReferenceType_throws() {
      assertThatThrownBy(() -> new GetImagesByReferenceCommand(null, 1L).validate())
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("실패 케이스 — referenceId 검증")
  class ReferenceIdValidationFailures {

    @Test
    @DisplayName("[TC-IMAGE-DOMAIN-013] referenceId=null → IllegalArgumentException")
    void validate_nullReferenceId_throws() {
      assertThatThrownBy(() -> new GetImagesByReferenceCommand(FREE, null).validate())
          .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -100L})
    @DisplayName("[TC-IMAGE-DOMAIN-013] referenceId=0 또는 음수 → IllegalArgumentException")
    void validate_nonPositiveReferenceId_throws(long referenceId) {
      assertThatThrownBy(() -> new GetImagesByReferenceCommand(FREE, referenceId).validate())
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
