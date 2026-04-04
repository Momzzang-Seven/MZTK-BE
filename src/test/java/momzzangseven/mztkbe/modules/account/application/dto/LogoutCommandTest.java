package momzzangseven.mztkbe.modules.account.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LogoutCommand unit test")
class LogoutCommandTest {

  @Test
  @DisplayName("of() stores refresh token")
  void of_storesRefreshToken() {
    LogoutCommand command = LogoutCommand.of("refresh-token");

    assertThat(command.getRefreshToken()).isEqualTo("refresh-token");
  }

  @Test
  @DisplayName("of() allows null refresh token")
  void of_allowsNullRefreshToken() {
    LogoutCommand command = LogoutCommand.of(null);

    assertThat(command.getRefreshToken()).isNull();
  }
}
