package momzzangseven.mztkbe.modules.account.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StepUpResult unit test")
class StepUpResultTest {

  @Test
  @DisplayName("of() sets Bearer grant type")
  void of_setsBearerGrantType() {
    StepUpResult result = StepUpResult.of("step-up-token", 120L);

    assertThat(result.accessToken()).isEqualTo("step-up-token");
    assertThat(result.grantType()).isEqualTo("Bearer");
    assertThat(result.expiresIn()).isEqualTo(120L);
  }

  @Test
  @DisplayName("constructor keeps provided grantType")
  void constructor_keepsProvidedGrantType() {
    StepUpResult result = new StepUpResult("token", "Custom", -1L);

    assertThat(result.grantType()).isEqualTo("Custom");
    assertThat(result.expiresIn()).isEqualTo(-1L);
  }
}
