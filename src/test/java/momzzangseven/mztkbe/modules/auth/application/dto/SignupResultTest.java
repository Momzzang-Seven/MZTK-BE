package momzzangseven.mztkbe.modules.auth.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignupResult unit test")
class SignupResultTest {

  @Test
  @DisplayName("from() maps id, email, nickname from user")
  void from_mapsFieldsFromUser() {
    User user =
        User.builder()
            .id(5L)
            .email("user@example.com")
            .nickname("tester")
            .authProvider(AuthProvider.LOCAL)
            .role(UserRole.USER)
            .build();

    SignupResult result = SignupResult.from(user);

    assertThat(result.userId()).isEqualTo(5L);
    assertThat(result.email()).isEqualTo("user@example.com");
    assertThat(result.nickname()).isEqualTo("tester");
  }

  @Test
  @DisplayName("from() with null user throws NullPointerException")
  void from_withNullUser_throwsNullPointerException() {
    assertThatThrownBy(() -> SignupResult.from(null)).isInstanceOf(NullPointerException.class);
  }
}
