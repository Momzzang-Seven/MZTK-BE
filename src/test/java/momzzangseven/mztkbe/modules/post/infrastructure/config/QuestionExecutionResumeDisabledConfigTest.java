package momzzangseven.mztkbe.modules.post.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QuestionExecutionResumeDisabledConfig unit test")
class QuestionExecutionResumeDisabledConfigTest {

  @Test
  @DisplayName("disabled fallback returns empty optional")
  void disabledFallbackReturnsEmptyOptional() {
    var port = new QuestionExecutionResumeDisabledConfig().loadQuestionExecutionResumePort();

    assertThat(port.loadLatest(1L)).isEmpty();
  }
}
