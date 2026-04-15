package momzzangseven.mztkbe.modules.answer.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnswerExecutionResumeStubConfig unit test")
class AnswerExecutionResumeStubConfigTest {

  @Test
  @DisplayName("stub returns empty optional for read resume")
  void stubReturnsEmptyOptional() {
    var port = new AnswerExecutionResumeStubConfig().loadAnswerExecutionResumePort();

    assertThat(port.loadLatest(20L)).isEmpty();
    assertThat(port.loadLatestByAnswerIds(java.util.List.of(20L, 21L))).isEmpty();
  }
}
