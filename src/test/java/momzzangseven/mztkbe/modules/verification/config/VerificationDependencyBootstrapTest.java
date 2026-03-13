package momzzangseven.mztkbe.modules.verification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class VerificationDependencyBootstrapTest {

  @Test
  @DisplayName("verification prompt resource 3요소가 모두 존재한다")
  void promptResourcesExist() {
    assertResourceExists("prompts/verification/workout-photo-system-instruction.txt");
    assertResourceExists("prompts/verification/workout-photo-user-prompt.txt");
    assertResourceExists("prompts/verification/workout-photo-response-schema.json");

    assertResourceExists("prompts/verification/workout-record-system-instruction.txt");
    assertResourceExists("prompts/verification/workout-record-user-prompt.txt");
    assertResourceExists("prompts/verification/workout-record-response-schema.json");
  }

  @Test
  @DisplayName("fixed user prompt가 계약과 일치한다")
  void fixedUserPromptMatchesContract() throws Exception {
    String photoExpected =
        "Analyze the attached normalized workout-photo candidate and return only the result JSON.";
    String recordExpected =
        "Analyze the attached normalized workout-record candidate and return only the result JSON.";
    String photoPrompt =
        new ClassPathResource("prompts/verification/workout-photo-user-prompt.txt")
            .getContentAsString(StandardCharsets.UTF_8)
            .trim();
    String recordPrompt =
        new ClassPathResource("prompts/verification/workout-record-user-prompt.txt")
            .getContentAsString(StandardCharsets.UTF_8)
            .trim();

    assertThat(photoPrompt).isEqualTo(photoExpected);
    assertThat(recordPrompt).isEqualTo(recordExpected);
  }

  private void assertResourceExists(String path) {
    assertThat(new ClassPathResource(path).exists()).isTrue();
  }
}
