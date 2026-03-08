package momzzangseven.mztkbe.modules.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.user.IllegalAdminGrantException;
import momzzangseven.mztkbe.global.error.user.InvalidUserRoleException;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("User unit test")
class UserTest {

  @Test
  @DisplayName("createFromLocal builds active local user with default role")
  void createFromLocal_withValidInput_createsActiveUser() {
    User user = User.createFromLocal("local@example.com", bcrypt(), "local-user");

    assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
    assertThat(user.getRole()).isEqualTo(UserRole.USER);
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getDeletedAt()).isNull();
    assertThat(user.getCreatedAt()).isNotNull();
    assertThat(user.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("createFromLocal rejects non-bcrypt password")
  void createFromLocal_withInvalidEncodedPassword_throws() {
    assertThatThrownBy(() -> User.createFromLocal("local@example.com", "not-bcrypt", "nick"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Password must be BCrypt encoded format");
  }

  @Test
  @DisplayName("createFromLocal rejects missing email")
  void createFromLocal_withMissingEmail_throws() {
    assertThatThrownBy(() -> User.createFromLocal(" ", bcrypt(), "nick"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Email is required");
  }

  @Test
  @DisplayName("createFromLocal rejects null email")
  void createFromLocal_withNullEmail_throws() {
    assertThatThrownBy(() -> User.createFromLocal(null, bcrypt(), "nick"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Email is required");
  }

  @Test
  @DisplayName("createFromLocal rejects invalid email format")
  void createFromLocal_withInvalidEmailFormat_throws() {
    assertThatThrownBy(() -> User.createFromLocal("invalid-email", bcrypt(), "nick"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid email format");
  }

  @Test
  @DisplayName("createFromLocal rejects missing password")
  void createFromLocal_withMissingPassword_throws() {
    assertThatThrownBy(() -> User.createFromLocal("local@example.com", " ", "nick"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Password is required");
  }

  @Test
  @DisplayName("createFromLocal rejects null password")
  void createFromLocal_withNullPassword_throws() {
    assertThatThrownBy(() -> User.createFromLocal("local@example.com", null, "nick"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Password is required");
  }

  @Test
  @DisplayName("createFromLocal rejects missing nickname")
  void createFromLocal_withMissingNickname_throws() {
    assertThatThrownBy(() -> User.createFromLocal("local@example.com", bcrypt(), " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Nickname is required");
  }

  @Test
  @DisplayName("createFromLocal rejects null nickname")
  void createFromLocal_withNullNickname_throws() {
    assertThatThrownBy(() -> User.createFromLocal("local@example.com", bcrypt(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Nickname is required");
  }

  @Test
  @DisplayName("createFromLocal rejects too short nickname")
  void createFromLocal_withTooShortNickname_throws() {
    assertThatThrownBy(() -> User.createFromLocal("local@example.com", bcrypt(), "a"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Nickname must be between 2 and 50 characters");
  }

  @Test
  @DisplayName("createFromLocal rejects too long nickname")
  void createFromLocal_withTooLongNickname_throws() {
    assertThatThrownBy(() -> User.createFromLocal("local@example.com", bcrypt(), "n".repeat(51)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Nickname must be between 2 and 50 characters");
  }

  @Test
  @DisplayName("createFromGoogle rejects blank provider id")
  void createFromGoogle_withBlankProviderId_throws() {
    assertThatThrownBy(() -> User.createFromGoogle(" ", "g@example.com", "nick", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Google ID is required");
  }

  @Test
  @DisplayName("createFromGoogle rejects null provider id")
  void createFromGoogle_withNullProviderId_throws() {
    assertThatThrownBy(() -> User.createFromGoogle(null, "g@example.com", "nick", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Google ID is required");
  }

  @Test
  @DisplayName("createFromGoogle builds active google user")
  void createFromGoogle_withValidInput_createsUser() {
    User user = User.createFromGoogle("google-id", "g@example.com", "nick", null);

    assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  @DisplayName("createFromKakao rejects null provider id")
  void createFromKakao_withNullProviderId_throws() {
    assertThatThrownBy(() -> User.createFromKakao(null, "k@example.com", "nick", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Kakao ID is required");
  }

  @Test
  @DisplayName("updateRole rejects null role")
  void updateRole_withNullRole_throws() {
    User user = baseUser(AuthProvider.LOCAL, UserRole.USER, UserStatus.ACTIVE);

    assertThatThrownBy(() -> user.updateRole(null))
        .isInstanceOf(InvalidUserRoleException.class)
        .hasMessageContaining("Cannot update user role if no role is provided");
  }

  @Test
  @DisplayName("updateRole rejects same role")
  void updateRole_withSameRole_throws() {
    User user = baseUser(AuthProvider.LOCAL, UserRole.USER, UserStatus.ACTIVE);

    assertThatThrownBy(() -> user.updateRole(UserRole.USER))
        .isInstanceOf(InvalidUserRoleException.class)
        .hasMessageContaining("New role is same as current role");
  }

  @Test
  @DisplayName("updateRole rejects admin grant")
  void updateRole_withAdminRole_throws() {
    User user = baseUser(AuthProvider.LOCAL, UserRole.USER, UserStatus.ACTIVE);

    assertThatThrownBy(() -> user.updateRole(UserRole.ADMIN))
        .isInstanceOf(IllegalAdminGrantException.class);
  }

  @Test
  @DisplayName("updateRole changes role for valid transition")
  void updateRole_withValidRole_updatesRole() {
    User user = baseUser(AuthProvider.LOCAL, UserRole.USER, UserStatus.ACTIVE);

    User updated = user.updateRole(UserRole.TRAINER);

    assertThat(updated.getRole()).isEqualTo(UserRole.TRAINER);
    assertThat(updated.getUpdatedAt()).isAfter(user.getUpdatedAt());
  }

  @Test
  @DisplayName("withdraw and reactivate transition status and deletedAt")
  void withdrawAndReactivate_transitionsState() {
    User user = baseUser(AuthProvider.KAKAO, UserRole.USER, UserStatus.ACTIVE);

    User withdrawn = user.withdraw();
    assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(withdrawn.getDeletedAt()).isNotNull();

    User reactivated = withdrawn.reactivate();
    assertThat(reactivated.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(reactivated.getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("updateGoogleRefreshToken only allows google users")
  void updateGoogleRefreshToken_withNonGoogleUser_throws() {
    User kakaoUser = baseUser(AuthProvider.KAKAO, UserRole.USER, UserStatus.ACTIVE);

    assertThatThrownBy(() -> kakaoUser.updateGoogleRefreshToken("encrypted"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Google refresh token can only be saved for GOOGLE users");
  }

  @Test
  @DisplayName("updateGoogleRefreshToken rejects blank token")
  void updateGoogleRefreshToken_withBlankToken_throws() {
    User googleUser = baseUser(AuthProvider.GOOGLE, UserRole.USER, UserStatus.ACTIVE);

    assertThatThrownBy(() -> googleUser.updateGoogleRefreshToken(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("encryptedRefreshToken is required");
  }

  @Test
  @DisplayName("updateGoogleRefreshToken rejects null token")
  void updateGoogleRefreshToken_withNullToken_throws() {
    User googleUser = baseUser(AuthProvider.GOOGLE, UserRole.USER, UserStatus.ACTIVE);

    assertThatThrownBy(() -> googleUser.updateGoogleRefreshToken(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("encryptedRefreshToken is required");
  }

  @Test
  @DisplayName("updatePassword only allows local users")
  void updatePassword_withNonLocalUser_throws() {
    User googleUser = baseUser(AuthProvider.GOOGLE, UserRole.USER, UserStatus.ACTIVE);

    assertThatThrownBy(() -> googleUser.updatePassword(bcrypt()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Password update is only allowed for LOCAL users");
  }

  @Test
  @DisplayName("updatePassword rejects non-bcrypt password")
  void updatePassword_withInvalidEncodedPassword_throws() {
    User localUser = baseUser(AuthProvider.LOCAL, UserRole.USER, UserStatus.ACTIVE);

    assertThatThrownBy(() -> localUser.updatePassword("not-bcrypt"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Password must be BCrypt encoded format");
  }

  @Test
  @DisplayName("updatePassword updates encoded password for local user")
  void updatePassword_withLocalUser_updatesPassword() {
    User localUser = baseUser(AuthProvider.LOCAL, UserRole.USER, UserStatus.ACTIVE);
    String newPassword = "$2a$" + "b".repeat(56);

    localUser.updatePassword(newPassword);

    assertThat(localUser.getPassword()).isEqualTo(newPassword);
  }

  @Test
  @DisplayName("validatePassword throws for non-local users")
  void validatePassword_withNonLocalUser_throws() {
    User googleUser = baseUser(AuthProvider.GOOGLE, UserRole.USER, UserStatus.ACTIVE);
    PasswordEncoder encoder = mock(PasswordEncoder.class);

    assertThatThrownBy(() -> googleUser.validatePassword("pw", encoder))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("only allowed for LOCAL users");
  }

  @Test
  @DisplayName("validatePassword returns false when encoder throws")
  void validatePassword_whenEncoderFails_returnsFalse() {
    User localUser = baseUser(AuthProvider.LOCAL, UserRole.USER, UserStatus.ACTIVE);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(encoder.matches("raw", bcrypt())).thenThrow(new RuntimeException("encoder down"));

    boolean valid = localUser.validatePassword("raw", encoder);

    assertThat(valid).isFalse();
  }

  @Test
  @DisplayName("validatePassword returns false when stored password is blank")
  void validatePassword_whenStoredPasswordBlank_returnsFalse() {
    User localUser =
        User.builder()
            .id(21L)
            .email("user@example.com")
            .password(" ")
            .nickname("tester")
            .authProvider(AuthProvider.LOCAL)
            .providerUserId("provider-id")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .createdAt(LocalDateTime.now().minusDays(1))
            .updatedAt(LocalDateTime.now().minusHours(1))
            .build();
    PasswordEncoder encoder = mock(PasswordEncoder.class);

    assertThat(localUser.validatePassword("raw", encoder)).isFalse();
  }

  @Test
  @DisplayName("validatePassword returns false when stored password is null")
  void validatePassword_whenStoredPasswordNull_returnsFalse() {
    User localUser =
        User.builder()
            .id(21L)
            .email("user@example.com")
            .password(null)
            .nickname("tester")
            .authProvider(AuthProvider.LOCAL)
            .providerUserId("provider-id")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .createdAt(LocalDateTime.now().minusDays(1))
            .updatedAt(LocalDateTime.now().minusHours(1))
            .build();
    PasswordEncoder encoder = mock(PasswordEncoder.class);

    assertThat(localUser.validatePassword("raw", encoder)).isFalse();
  }

  @Test
  @DisplayName("validatePassword returns false when raw password is blank")
  void validatePassword_whenRawPasswordBlank_returnsFalse() {
    User localUser = baseUser(AuthProvider.LOCAL, UserRole.USER, UserStatus.ACTIVE);
    PasswordEncoder encoder = mock(PasswordEncoder.class);

    assertThat(localUser.validatePassword(" ", encoder)).isFalse();
  }

  @Test
  @DisplayName("validatePassword returns false when raw password is null")
  void validatePassword_whenRawPasswordNull_returnsFalse() {
    User localUser = baseUser(AuthProvider.LOCAL, UserRole.USER, UserStatus.ACTIVE);
    PasswordEncoder encoder = mock(PasswordEncoder.class);

    assertThat(localUser.validatePassword(null, encoder)).isFalse();
  }

  @Test
  @DisplayName("validatePassword returns true when password matches")
  void validatePassword_whenEncoderMatches_returnsTrue() {
    User localUser = baseUser(AuthProvider.LOCAL, UserRole.USER, UserStatus.ACTIVE);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(encoder.matches("raw", bcrypt())).thenReturn(true);

    assertThat(localUser.validatePassword("raw", encoder)).isTrue();
  }

  @Test
  @DisplayName("validatePassword returns false when password mismatches")
  void validatePassword_whenEncoderNotMatches_returnsFalse() {
    User localUser = baseUser(AuthProvider.LOCAL, UserRole.USER, UserStatus.ACTIVE);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(encoder.matches("raw", bcrypt())).thenReturn(false);

    assertThat(localUser.validatePassword("raw", encoder)).isFalse();
  }

  @Test
  @DisplayName("updateGoogleRefreshToken updates token for google user")
  void updateGoogleRefreshToken_withGoogleUser_updatesToken() {
    User googleUser = baseUser(AuthProvider.GOOGLE, UserRole.USER, UserStatus.ACTIVE);

    User updated = googleUser.updateGoogleRefreshToken("encrypted-token");

    assertThat(updated.getGoogleRefreshToken()).isEqualTo("encrypted-token");
  }

  @Test
  @DisplayName("canBecomeTrainer returns true when email exists")
  void canBecomeTrainer_whenEmailExists_returnsTrue() {
    User user = baseUser(AuthProvider.LOCAL, UserRole.USER, UserStatus.ACTIVE);

    assertThat(user.canBecomeTrainer()).isTrue();
  }

  @Test
  @DisplayName("canBecomeTrainer returns false when email null")
  void canBecomeTrainer_whenEmailNull_returnsFalse() {
    User user =
        User.builder()
            .id(1L)
            .email(null)
            .authProvider(AuthProvider.KAKAO)
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    assertThat(user.canBecomeTrainer()).isFalse();
  }

  private User baseUser(AuthProvider provider, UserRole role, UserStatus status) {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 18, 0);
    return User.builder()
        .id(21L)
        .email("user@example.com")
        .password(bcrypt())
        .nickname("tester")
        .authProvider(provider)
        .providerUserId("provider-id")
        .role(role)
        .status(status)
        .createdAt(now.minusDays(10))
        .updatedAt(now.minusDays(1))
        .build();
  }

  private String bcrypt() {
    return "$2a$" + "a".repeat(56);
  }
}
