package momzzangseven.mztkbe.modules.answer.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateAnswerResult unit test")
class CreateAnswerResultTest {

  @Test
  @DisplayName("constructor preserves ids")
  void constructor_preservesIds() {
    CreateAnswerResult result = new CreateAnswerResult(88L, 123L, null);

    assertThat(result.postId()).isEqualTo(88L);
    assertThat(result.answerId()).isEqualTo(123L);
  }
}
