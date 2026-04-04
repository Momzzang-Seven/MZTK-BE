package momzzangseven.mztkbe.modules.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.user.IllegalAdminGrantException;
import momzzangseven.mztkbe.global.error.user.InvalidUserRoleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("User unit test")
class UserTest {

  // ── User.create() factory method tests ──

  @Test
  @DisplayName("[M-32] create: provider 없이 순수 프로필 User 생성")
  void create_withValidInput_createsProfileOnlyUser() {
    User user = User.create("new@example.com", "newbie", "https://img.com/1.png", UserRole.USER);

    assertThat(user.getEmail()).isEqualTo("new@example.com");
    assertThat(user.getNickname()).isEqualTo("newbie");
    assertThat(user.getProfileImageUrl()).isEqualTo("https://img.com/1.png");
    assertThat(user.getRole()).isEqualTo(UserRole.USER);
    assertThat(user.getCreatedAt()).isNotNull();
    assertThat(user.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("[M-33] create: TRAINER 역할로 생성 가능")
  void create_withTrainerRole_setsTrainerRole() {
    User user = User.create("trainer@example.com", "trainer", null, UserRole.TRAINER);

    assertThat(user.getRole()).isEqualTo(UserRole.TRAINER);
    assertThat(user.getProfileImageUrl()).isNull();
  }

  @Test
  @DisplayName("[M-34] create: 이메일 형식 잘못된 경우 IllegalArgumentException")
  void create_withInvalidEmail_throws() {
    assertThatThrownBy(() -> User.create("invalid-email", "test", null, UserRole.USER))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("[M-35] create: 닉네임 누락 시 IllegalArgumentException")
  void create_withNullNickname_throws() {
    assertThatThrownBy(() -> User.create("test@example.com", null, null, UserRole.USER))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("[M-36] create: 닉네임 길이 1자(최소 미만) 시 IllegalArgumentException")
  void create_withTooShortNickname_throws() {
    assertThatThrownBy(() -> User.create("test@example.com", "a", null, UserRole.USER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Nickname must be between 2 and 50 characters");
  }

  // ── updateRole tests ──

  @Test
  @DisplayName("updateRole rejects null role")
  void updateRole_withNullRole_throws() {
    User user = baseUser(UserRole.USER);

    assertThatThrownBy(() -> user.updateRole(null))
        .isInstanceOf(InvalidUserRoleException.class)
        .hasMessageContaining("Cannot update user role if no role is provided");
  }

  @Test
  @DisplayName("updateRole rejects same role")
  void updateRole_withSameRole_throws() {
    User user = baseUser(UserRole.USER);

    assertThatThrownBy(() -> user.updateRole(UserRole.USER))
        .isInstanceOf(InvalidUserRoleException.class)
        .hasMessageContaining("New role is same as current role");
  }

  @Test
  @DisplayName("updateRole rejects admin grant")
  void updateRole_withAdminRole_throws() {
    User user = baseUser(UserRole.USER);

    assertThatThrownBy(() -> user.updateRole(UserRole.ADMIN))
        .isInstanceOf(IllegalAdminGrantException.class);
  }

  @Test
  @DisplayName("updateRole changes role for valid transition")
  void updateRole_withValidRole_updatesRole() {
    User user = baseUser(UserRole.USER);

    User updated = user.updateRole(UserRole.TRAINER);

    assertThat(updated.getRole()).isEqualTo(UserRole.TRAINER);
    assertThat(updated.getUpdatedAt()).isAfter(user.getUpdatedAt());
  }

  // ── canBecomeTrainer tests ──

  @Test
  @DisplayName("canBecomeTrainer returns true when email exists")
  void canBecomeTrainer_whenEmailExists_returnsTrue() {
    User user = baseUser(UserRole.USER);

    assertThat(user.canBecomeTrainer()).isTrue();
  }

  @Test
  @DisplayName("canBecomeTrainer returns false when email null")
  void canBecomeTrainer_whenEmailNull_returnsFalse() {
    User user =
        User.builder()
            .id(1L)
            .email(null)
            .role(UserRole.USER)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    assertThat(user.canBecomeTrainer()).isFalse();
  }

  private User baseUser(UserRole role) {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 18, 0);
    return User.builder()
        .id(21L)
        .email("user@example.com")
        .nickname("tester")
        .role(role)
        .createdAt(now.minusDays(10))
        .updatedAt(now.minusDays(1))
        .build();
  }
}
