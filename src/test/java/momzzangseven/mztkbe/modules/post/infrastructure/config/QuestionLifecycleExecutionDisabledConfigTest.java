package momzzangseven.mztkbe.modules.post.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QuestionLifecycleExecutionDisabledConfig unit test")
class QuestionLifecycleExecutionDisabledConfigTest {

  @Test
  @DisplayName("disabled fallback returns empty optional for all prepare operations")
  void disabledFallbackReturnsEmptyOptional() {
    var port = new QuestionLifecycleExecutionDisabledConfig().questionLifecycleExecutionPort();

    assertThat(port.prepareQuestionCreate(1L, 2L, "q", 10L)).isEmpty();
    assertThat(port.prepareQuestionUpdate(1L, 2L, "q", 10L, 1L, "token")).isEmpty();
    assertThat(port.prepareQuestionDelete(1L, 2L, "q", 10L)).isEmpty();
    assertThat(port.prepareAnswerAccept(1L, 3L, 2L, 4L, "q", "a", 10L)).isEmpty();
  }
}
