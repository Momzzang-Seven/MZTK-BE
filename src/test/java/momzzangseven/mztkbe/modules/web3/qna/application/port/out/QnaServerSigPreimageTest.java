package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QnaServerSigPreimage} — verifies that the sealed hierarchy contains exactly
 * the 7 expected nested record subtypes.
 *
 * <p>Covers test case S-401 (Commit 2, Section S).
 */
@DisplayName("QnaServerSigPreimage 단위 테스트")
class QnaServerSigPreimageTest {

  // =========================================================================
  // Section S — Permitted subclasses
  // =========================================================================

  @Nested
  @DisplayName("S. 허용된 하위 타입 완전성")
  class PermittedSubclasses {

    @Test
    @DisplayName("[S-401] 허용된 하위 타입이 정확히 7개이며 기대 레코드 타입과 일치함")
    void permittedSubclasses_exactlySevenExpectedRecordTypes() {
      // when
      Class<?>[] permitted = QnaServerSigPreimage.class.getPermittedSubclasses();

      // then
      assertThat(permitted).hasSize(7);
      assertThat(permitted)
          .containsExactlyInAnyOrder(
              QnaServerSigPreimage.CreateQuestionPreimage.class,
              QnaServerSigPreimage.UpdateQuestionPreimage.class,
              QnaServerSigPreimage.DeleteQuestionPreimage.class,
              QnaServerSigPreimage.SubmitAnswerPreimage.class,
              QnaServerSigPreimage.UpdateAnswerPreimage.class,
              QnaServerSigPreimage.DeleteAnswerPreimage.class,
              QnaServerSigPreimage.AcceptAnswerPreimage.class);
    }
  }
}
