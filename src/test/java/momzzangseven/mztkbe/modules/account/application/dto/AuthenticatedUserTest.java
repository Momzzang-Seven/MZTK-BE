package momzzangseven.mztkbe.modules.account.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuthenticatedUser unit test")
class AuthenticatedUserTest {

  @Test
  @DisplayName("existing() sets isNewUser to false")
  void existing_setsIsNewUserFalse() {
    AccountUserSnapshot snapshot = sampleSnapshot();

    AuthenticatedUser result = AuthenticatedUser.existing(snapshot);

    assertThat(result.userSnapshot()).isSameAs(snapshot);
    assertThat(result.isNewUser()).isFalse();
  }

  @Test
  @DisplayName("newUser() sets isNewUser to true")
  void newUser_setsIsNewUserTrue() {
    AccountUserSnapshot snapshot = sampleSnapshot();

    AuthenticatedUser result = AuthenticatedUser.newUser(snapshot);

    assertThat(result.userSnapshot()).isSameAs(snapshot);
    assertThat(result.isNewUser()).isTrue();
  }

  private AccountUserSnapshot sampleSnapshot() {
    return new AccountUserSnapshot(1L, "user@example.com", "tester", null, "USER");
  }
}
