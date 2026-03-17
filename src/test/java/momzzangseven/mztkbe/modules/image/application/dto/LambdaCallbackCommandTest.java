package momzzangseven.mztkbe.modules.image.application.dto;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.modules.image.domain.vo.LambdaCallbackStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** LambdaCallbackCommand.validate() 단위 테스트. */
@DisplayName("LambdaCallbackCommand validate() 단위 테스트")
class LambdaCallbackCommandTest {

  @Nested
  @DisplayName("성공 케이스 — validate() 통과")
  class SuccessCases {

    @Test
    @DisplayName("[V-OK-1] COMPLETED + 올바른 tmpObjectKey + finalObjectKey → 예외 없음")
    void validate_completed_withAllRequiredFields_passes() {
      assertThatNoException()
          .isThrownBy(
              () ->
                  new LambdaCallbackCommand(
                          LambdaCallbackStatus.COMPLETED, "tmp.jpg", "final.webp", null)
                      .validate());
    }

    @Test
    @DisplayName("[V-OK-2] FAILED + finalObjectKey=null + errorReason 있음 → 예외 없음")
    void validate_failed_withNullFinalKey_passes() {
      assertThatNoException()
          .isThrownBy(
              () ->
                  new LambdaCallbackCommand(LambdaCallbackStatus.FAILED, "tmp.jpg", null, "OOM")
                      .validate());
    }

    @Test
    @DisplayName(
        "[V-OK-3] FAILED + finalObjectKey=null + errorReason=null → 예외 없음 (errorReason 선택)")
    void validate_failed_withBothNull_passes() {
      assertThatNoException()
          .isThrownBy(
              () ->
                  new LambdaCallbackCommand(LambdaCallbackStatus.FAILED, "tmp.jpg", null, null)
                      .validate());
    }
  }

  @Nested
  @DisplayName("실패 케이스 — status 검증")
  class StatusValidationFailures {

    @Test
    @DisplayName("[V-1] status=null → IllegalArgumentException")
    void validate_nullStatus_throws() {
      assertThatThrownBy(
              () -> new LambdaCallbackCommand(null, "tmp.jpg", "final.webp", null).validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("status");
    }
  }

  @Nested
  @DisplayName("실패 케이스 — tmpObjectKey 검증")
  class TmpObjectKeyValidationFailures {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("[V-2] tmpObjectKey가 null 또는 공백 → IllegalArgumentException")
    void validate_blankTmpObjectKey_throws(String tmpKey) {
      assertThatThrownBy(
              () ->
                  new LambdaCallbackCommand(
                          LambdaCallbackStatus.COMPLETED, tmpKey, "final.webp", null)
                      .validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("tmpObjectKey");
    }
  }

  @Nested
  @DisplayName("실패 케이스 — COMPLETED 전용 검증")
  class CompletedStatusValidationFailures {

    @Test
    @DisplayName("[V-3] COMPLETED인데 finalObjectKey=null → IllegalArgumentException")
    void validate_completedWithNullFinalKey_throws() {
      assertThatThrownBy(
              () ->
                  new LambdaCallbackCommand(LambdaCallbackStatus.COMPLETED, "tmp.jpg", null, null)
                      .validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("finalObjectKey");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("[V-4] COMPLETED인데 finalObjectKey=공백 → IllegalArgumentException")
    void validate_completedWithBlankFinalKey_throws(String finalKey) {
      assertThatThrownBy(
              () ->
                  new LambdaCallbackCommand(
                          LambdaCallbackStatus.COMPLETED, "tmp.jpg", finalKey, null)
                      .validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("finalObjectKey");
    }
  }

  @Nested
  @DisplayName("실패 케이스 — FAILED 전용 검증")
  class FailedStatusValidationFailures {

    @Test
    @DisplayName("[V-5] FAILED인데 finalObjectKey가 존재 → IllegalArgumentException")
    void validate_failedWithFinalKey_throws() {
      assertThatThrownBy(
              () ->
                  new LambdaCallbackCommand(
                          LambdaCallbackStatus.FAILED, "tmp.jpg", "final.webp", null)
                      .validate())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("finalObjectKey");
    }
  }
}
