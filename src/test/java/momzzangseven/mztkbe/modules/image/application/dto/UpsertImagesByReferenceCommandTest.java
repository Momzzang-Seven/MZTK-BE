package momzzangseven.mztkbe.modules.image.application.dto;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** UpsertImagesByReferenceCommand.validate() 단위 테스트. */
@DisplayName("UpsertImagesByReferenceCommand validate() 단위 테스트")
class UpsertImagesByReferenceCommandTest {

  private static final Long VALID_USER = 10L;
  private static final Long VALID_REF = 5L;
  private static final ImageReferenceType FREE = ImageReferenceType.COMMUNITY_FREE;

  @Nested
  @DisplayName("성공 케이스 — validate() 통과")
  class SuccessCases {

    @Test
    @DisplayName("[V-OK-1] 모든 필드 유효 + imageIds 빈 리스트 → 예외 없음")
    void validate_allValidFieldsEmptyImageIds_passes() {
      assertThatNoException()
          .isThrownBy(
              () ->
                  new UpsertImagesByReferenceCommand(VALID_USER, VALID_REF, FREE, List.of())
                      .validate());
    }

    @Test
    @DisplayName("[V-OK-2] 모든 필드 유효 + imageIds=[1,2,3] → 예외 없음")
    void validate_allValidFieldsWithImageIds_passes() {
      assertThatNoException()
          .isThrownBy(
              () ->
                  new UpsertImagesByReferenceCommand(
                          VALID_USER, VALID_REF, FREE, List.of(1L, 2L, 3L))
                      .validate());
    }
  }

  @Nested
  @DisplayName("실패 케이스 — userId 검증")
  class UserIdValidationFailures {

    @Test
    @DisplayName("[TC-IMAGE-DOMAIN-009] userId=null → IllegalArgumentException")
    void validate_nullUserId_throws() {
      assertThatThrownBy(
              () -> new UpsertImagesByReferenceCommand(null, VALID_REF, FREE, List.of()).validate())
          .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("[TC-IMAGE-DOMAIN-009] userId=0 또는 음수 → IllegalArgumentException")
    void validate_nonPositiveUserId_throws(long userId) {
      assertThatThrownBy(
              () ->
                  new UpsertImagesByReferenceCommand(userId, VALID_REF, FREE, List.of()).validate())
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("실패 케이스 — referenceId 검증")
  class ReferenceIdValidationFailures {

    @Test
    @DisplayName("[TC-IMAGE-DOMAIN-010] referenceId=null → IllegalArgumentException")
    void validate_nullReferenceId_throws() {
      assertThatThrownBy(
              () ->
                  new UpsertImagesByReferenceCommand(VALID_USER, null, FREE, List.of()).validate())
          .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("[TC-IMAGE-DOMAIN-010] referenceId=0 또는 음수 → IllegalArgumentException")
    void validate_nonPositiveReferenceId_throws(long refId) {
      assertThatThrownBy(
              () ->
                  new UpsertImagesByReferenceCommand(VALID_USER, refId, FREE, List.of()).validate())
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("실패 케이스 — referenceType 검증")
  class ReferenceTypeValidationFailures {

    @Test
    @DisplayName("[TC-IMAGE-DOMAIN-011] referenceType=null → IllegalArgumentException")
    void validate_nullReferenceType_throws() {
      assertThatThrownBy(
              () ->
                  new UpsertImagesByReferenceCommand(VALID_USER, VALID_REF, null, List.of())
                      .validate())
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("실패 케이스 — imageIds 검증")
  class ImageIdsValidationFailures {

    @Test
    @DisplayName("[TC-IMAGE-DOMAIN-008] imageIds=null → IllegalArgumentException")
    void validate_nullImageIds_throws() {
      assertThatThrownBy(
              () ->
                  new UpsertImagesByReferenceCommand(VALID_USER, VALID_REF, FREE, null).validate())
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
