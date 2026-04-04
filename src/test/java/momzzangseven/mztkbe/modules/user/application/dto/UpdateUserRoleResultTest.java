package momzzangseven.mztkbe.modules.user.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateUserRoleResult unit test")
class UpdateUserRoleResultTest {

  @Test
  @DisplayName("from() maps user fields")
  void from_mapsFields() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 3, 1, 10, 0);
    LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 1, 10, 5);
    User user =
        User.builder()
            .id(1L)
            .email("user@example.com")
            .nickname("nick")
            .profileImageUrl("https://example.com/profile.png")
            .role(UserRole.TRAINER)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .build();

    UpdateUserRoleResult result = UpdateUserRoleResult.from(user);

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.email()).isEqualTo("user@example.com");
    assertThat(result.nickname()).isEqualTo("nick");
    assertThat(result.profileImageUrl()).isEqualTo("https://example.com/profile.png");
    assertThat(result.role()).isEqualTo(UserRole.TRAINER);
    assertThat(result.createdAt()).isEqualTo(createdAt);
    assertThat(result.updatedAt()).isEqualTo(updatedAt);
    assertThat(result.name()).isNull();
  }

  @Test
  @DisplayName("validate rejects non-positive user ID")
  void validate_nonPositiveId_throwsException() {
    UpdateUserRoleResult result =
        new UpdateUserRoleResult(
            0L, "user@example.com", null, "nick", null, UserRole.USER, null, null);

    assertThatThrownBy(result::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("User ID must be positive");
  }

  @Test
  @DisplayName("validate rejects blank email")
  void validate_blankEmail_throwsException() {
    UpdateUserRoleResult result =
        new UpdateUserRoleResult(1L, " ", null, "nick", null, UserRole.USER, null, null);

    assertThatThrownBy(result::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Email cannot be empty");
  }

  @Test
  @DisplayName("validate rejects null role")
  void validate_nullRole_throwsException() {
    UpdateUserRoleResult result =
        new UpdateUserRoleResult(1L, "user@example.com", null, "nick", null, null, null, null);

    assertThatThrownBy(result::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Role cannot be null");
  }

  @Test
  @DisplayName("validate passes with required fields")
  void validate_validResult_doesNotThrow() {
    UpdateUserRoleResult result =
        new UpdateUserRoleResult(
            1L, "user@example.com", null, "nick", null, UserRole.USER, null, null);

    assertThatCode(result::validate).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("validate rejects null id")
  void validate_nullId_throwsException() {
    UpdateUserRoleResult result =
        new UpdateUserRoleResult(
            null, "user@example.com", null, "nick", null, UserRole.USER, null, null);

    assertThatThrownBy(result::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("User ID must be positive");
  }

  @Test
  @DisplayName("validate rejects null email")
  void validate_nullEmail_throwsException() {
    UpdateUserRoleResult result =
        new UpdateUserRoleResult(1L, null, null, "nick", null, UserRole.USER, null, null);

    assertThatThrownBy(result::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Email cannot be empty");
  }
}
