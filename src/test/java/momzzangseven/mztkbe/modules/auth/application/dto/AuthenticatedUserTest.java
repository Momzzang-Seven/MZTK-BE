package momzzangseven.mztkbe.modules.auth.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuthenticatedUser unit test")
class AuthenticatedUserTest {

  @Test
  @DisplayName("existing() sets isNewUser to false")
  void existing_setsIsNewUserFalse() {
    User user = sampleUser();

    AuthenticatedUser result = AuthenticatedUser.existing(user);

    assertThat(result.user()).isSameAs(user);
    assertThat(result.isNewUser()).isFalse();
  }

  @Test
  @DisplayName("newUser() sets isNewUser to true")
  void newUser_setsIsNewUserTrue() {
    User user = sampleUser();

    AuthenticatedUser result = AuthenticatedUser.newUser(user);

    assertThat(result.user()).isSameAs(user);
    assertThat(result.isNewUser()).isTrue();
  }

  private User sampleUser() {
    return User.builder()
        .id(1L)
        .email("user@example.com")
        .authProvider(AuthProvider.LOCAL)
        .role(UserRole.USER)
        .build();
  }
}
