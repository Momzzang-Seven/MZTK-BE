package momzzangseven.mztkbe.modules.image.application.dto;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("GetImagesByReferencesCommand validate() 단위 테스트")
class GetImagesByReferencesCommandTest {

  private static final ImageReferenceType FREE = ImageReferenceType.COMMUNITY_FREE;

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("referenceType과 양수 referenceIds가 주어지면 validate()를 통과한다")
    void validate_validInputs_passes() {
      assertThatNoException()
          .isThrownBy(() -> new GetImagesByReferencesCommand(FREE, List.of(1L, 2L)).validate());
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("referenceType=null 이면 IllegalArgumentException")
    void validate_nullReferenceType_throws() {
      assertThatThrownBy(() -> new GetImagesByReferencesCommand(null, List.of(1L)).validate())
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("referenceIds=null 이면 IllegalArgumentException")
    void validate_nullReferenceIds_throws() {
      assertThatThrownBy(() -> new GetImagesByReferencesCommand(FREE, null).validate())
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("referenceIds 안에 null 이 있으면 IllegalArgumentException")
    void validate_nullReferenceIdElement_throws() {
      assertThatThrownBy(
              () -> new GetImagesByReferencesCommand(FREE, Arrays.asList(1L, null)).validate())
          .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("referenceIds 안에 0 이하 값이 있으면 IllegalArgumentException")
    void validate_nonPositiveReferenceId_throws(long referenceId) {
      assertThatThrownBy(
              () -> new GetImagesByReferencesCommand(FREE, List.of(1L, referenceId)).validate())
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
