package momzzangseven.mztkbe.modules.account.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignupResult unit test")
class SignupResultTest {

  @Test
  @DisplayName("of() maps id, email, nickname")
  void of_mapsFields() {
    SignupResult result = SignupResult.of(5L, "user@example.com", "tester");

    assertThat(result.userId()).isEqualTo(5L);
    assertThat(result.email()).isEqualTo("user@example.com");
    assertThat(result.nickname()).isEqualTo("tester");
  }
}
