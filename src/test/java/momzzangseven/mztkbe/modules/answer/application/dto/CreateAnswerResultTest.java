package momzzangseven.mztkbe.modules.answer.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateAnswerResult unit test")
class CreateAnswerResultTest {

  @Test
  @DisplayName("constructor preserves answerId")
  void constructor_preservesAnswerId() {
    CreateAnswerResult result = new CreateAnswerResult(123L);

    assertThat(result.answerId()).isEqualTo(123L);
  }
}
