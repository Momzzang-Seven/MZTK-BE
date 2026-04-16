package momzzangseven.mztkbe.modules.post.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QuestionExecutionResumeStubConfig unit test")
class QuestionExecutionResumeStubConfigTest {

  @Test
  @DisplayName("stub returns empty optional for read resume")
  void stubReturnsEmptyOptional() {
    var port = new QuestionExecutionResumeStubConfig().loadQuestionExecutionResumePort();

    assertThat(port.loadLatest(10L)).isEmpty();
  }
}
