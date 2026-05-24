package momzzangseven.mztkbe.modules.answer.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnswerLifecycleExecutionDisabledConfig unit test")
class AnswerLifecycleExecutionDisabledConfigTest {

  @Test
  @DisplayName("disabled fallback returns empty optional for all prepare operations")
  void disabledFallbackReturnsEmptyOptional() {
    var port = new AnswerLifecycleExecutionDisabledConfig().answerLifecycleExecutionPort();

    assertThat(port.prepareAnswerCreate(1L, 2L, 3L, 4L, "q", 10L, "a", 1)).isEmpty();
    assertThat(port.prepareAnswerUpdate(1L, 2L, 3L, 4L, "q", 10L, "a", 1)).isEmpty();
    assertThat(port.prepareAnswerDelete(1L, 2L, 3L, 4L, "q", 10L, 0)).isEmpty();
  }
}
