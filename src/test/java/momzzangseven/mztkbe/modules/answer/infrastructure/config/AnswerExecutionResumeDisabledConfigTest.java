package momzzangseven.mztkbe.modules.answer.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnswerExecutionResumeDisabledConfig unit test")
class AnswerExecutionResumeDisabledConfigTest {

  @Test
  @DisplayName("disabled fallback returns empty optional")
  void disabledFallbackReturnsEmptyOptional() {
    var port = new AnswerExecutionResumeDisabledConfig().loadAnswerExecutionResumePort();

    assertThat(port.loadLatest(1L)).isEmpty();
  }
}
