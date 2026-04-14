package momzzangseven.mztkbe.modules.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import momzzangseven.mztkbe.global.error.user.IllegalAdminGrantException;
import momzzangseven.mztkbe.global.error.user.InvalidUserRoleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("User 단위 테스트")
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
  @DisplayName("[M-13] create: ADMIN role로 생성 시 IllegalAdminGrantException 발생")
  void create_withAdminRole_throwsIllegalAdminGrantException() {
    assertThatThrownBy(() -> User.create("admin@example.com", "adminUser", null, UserRole.ADMIN))
        .isInstanceOf(IllegalAdminGrantException.class);
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
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    assertThat(user.canBecomeTrainer()).isFalse();
  }

  // ══════════════════════════════════════════════════════════════
  // Commit 1 — MOM-330: createAdmin() factory method tests
  // ══════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("createAdmin 성공 케이스")
  class CreateAdminSuccessCases {

    @Test
    @DisplayName("[M-20] createAdmin with ADMIN_SEED creates admin user")
    void createAdmin_withAdminSeed_createsAdminUser() {
      // given / when
      User user = User.createAdmin("seed@admin.local", "SeedAdmin", UserRole.ADMIN_SEED);

      // then
      assertThat(user.getEmail()).isEqualTo("seed@admin.local");
      assertThat(user.getNickname()).isEqualTo("SeedAdmin");
      assertThat(user.getRole()).isEqualTo(UserRole.ADMIN_SEED);
      assertThat(user.getProfileImageUrl()).isNull();
      assertThat(user.getCreatedAt()).isNotNull();
      assertThat(user.getUpdatedAt()).isNotNull();
      assertThat(user.getCreatedAt()).isEqualTo(user.getUpdatedAt());
      assertThat(user.getId()).isNull();
    }

    @Test
    @DisplayName("[M-21] createAdmin with ADMIN_GENERATED creates admin user")
    void createAdmin_withAdminGenerated_createsAdminUser() {
      // given / when
      User user = User.createAdmin("gen@admin.local", "GenAdmin", UserRole.ADMIN_GENERATED);

      // then
      assertThat(user.getRole()).isEqualTo(UserRole.ADMIN_GENERATED);
      assertThat(user.getEmail()).isEqualTo("gen@admin.local");
      assertThat(user.getNickname()).isEqualTo("GenAdmin");
      assertThat(user.getProfileImageUrl()).isNull();
      assertThat(user.getCreatedAt()).isNotNull();
      assertThat(user.getUpdatedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("createAdmin 역할 검증")
  class CreateAdminRoleValidation {

    @Test
    @DisplayName("[M-22] createAdmin rejects ADMIN role (logical parent)")
    void createAdmin_withAdmin_throwsIllegalArgumentException() {
      assertThatThrownBy(() -> User.createAdmin("admin@admin.local", "PlainAdmin", UserRole.ADMIN))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not allowed");
    }

    @Test
    @DisplayName("[M-23] createAdmin rejects USER role")
    void createAdmin_withUser_throwsIllegalArgumentException() {
      assertThatThrownBy(() -> User.createAdmin("user@admin.local", "NotAdmin", UserRole.USER))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not allowed");
    }

    @Test
    @DisplayName("[M-24] createAdmin rejects TRAINER role")
    void createAdmin_withTrainer_throwsIllegalArgumentException() {
      assertThatThrownBy(
              () -> User.createAdmin("trainer@admin.local", "NotAdmin", UserRole.TRAINER))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not allowed");
    }
  }

  @Nested
  @DisplayName("createAdmin 입력값 검증")
  class CreateAdminInputValidation {

    @Test
    @DisplayName("[M-25] createAdmin rejects invalid email")
    void createAdmin_withInvalidEmail_throwsIllegalArgumentException() {
      assertThatThrownBy(() -> User.createAdmin("bad-email", "Admin", UserRole.ADMIN_SEED))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[M-26] createAdmin rejects null nickname")
    void createAdmin_withNullNickname_throwsIllegalArgumentException() {
      assertThatThrownBy(() -> User.createAdmin("admin@test.com", null, UserRole.ADMIN_SEED))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Nickname is required");
    }

    @Test
    @DisplayName("[M-27] createAdmin rejects too-short nickname")
    void createAdmin_withTooShortNickname_throwsIllegalArgumentException() {
      assertThatThrownBy(() -> User.createAdmin("admin@test.com", "A", UserRole.ADMIN_SEED))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Nickname must be between 2 and 50 characters");
    }
  }

  // ══════════════════════════════════════════════════════════════
  // Commit 1 — MOM-330: create() factory method — new admin guard
  // ══════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("create — isAdmin() 기반 관리자 차단 검증")
  class CreateAdminGuard {

    @Test
    @DisplayName("[M-28] create rejects ADMIN_SEED role")
    void create_withAdminSeed_throwsIllegalAdminGrantException() {
      assertThatThrownBy(() -> User.create("user@test.com", "tester", null, UserRole.ADMIN_SEED))
          .isInstanceOf(IllegalAdminGrantException.class);
    }

    @Test
    @DisplayName("[M-29] create rejects ADMIN_GENERATED role")
    void create_withAdminGenerated_throwsIllegalAdminGrantException() {
      assertThatThrownBy(
              () -> User.create("user@test.com", "tester", null, UserRole.ADMIN_GENERATED))
          .isInstanceOf(IllegalAdminGrantException.class);
    }

    @Test
    @DisplayName("[M-30] create still rejects plain ADMIN role (existing behavior preserved)")
    void create_withAdmin_throwsIllegalAdminGrantException() {
      assertThatThrownBy(() -> User.create("user@test.com", "tester", null, UserRole.ADMIN))
          .isInstanceOf(IllegalAdminGrantException.class);
    }
  }

  // ══════════════════════════════════════════════════════════════
  // Commit 1 — MOM-330: updateRole() — new admin guard
  // ══════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("updateRole — isAdmin() 기반 관리자 차단 검증")
  class UpdateRoleAdminGuard {

    @Test
    @DisplayName("[M-31] updateRole rejects ADMIN_SEED")
    void updateRole_withAdminSeed_throwsIllegalAdminGrantException() {
      // given
      User user = baseUser(UserRole.USER);

      // when / then
      assertThatThrownBy(() -> user.updateRole(UserRole.ADMIN_SEED))
          .isInstanceOf(IllegalAdminGrantException.class);
    }

    @Test
    @DisplayName("[M-32] updateRole rejects ADMIN_GENERATED")
    void updateRole_withAdminGenerated_throwsIllegalAdminGrantException() {
      // given
      User user = baseUser(UserRole.USER);

      // when / then
      assertThatThrownBy(() -> user.updateRole(UserRole.ADMIN_GENERATED))
          .isInstanceOf(IllegalAdminGrantException.class);
    }

    @Test
    @DisplayName("[M-33] updateRole still rejects plain ADMIN (existing behavior preserved)")
    void updateRole_withAdmin_throwsIllegalAdminGrantException() {
      // given
      User user = baseUser(UserRole.USER);

      // when / then
      assertThatThrownBy(() -> user.updateRole(UserRole.ADMIN))
          .isInstanceOf(IllegalAdminGrantException.class);
    }
  }

  // ══════════════════════════════════════════════════════════════
  // Commit 1 — MOM-330: isAdmin() delegation
  // ══════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("isAdmin — 역할별 관리자 여부 검증")
  class IsAdminDelegation {

    @Test
    @DisplayName("[M-34] User.isAdmin returns true when role is ADMIN_SEED")
    void isAdmin_withAdminSeed_returnsTrue() {
      // given
      User user = baseUser(UserRole.ADMIN_SEED);

      // when / then
      assertThat(user.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("[M-35] User.isAdmin returns true when role is ADMIN_GENERATED")
    void isAdmin_withAdminGenerated_returnsTrue() {
      // given
      User user = baseUser(UserRole.ADMIN_GENERATED);

      // when / then
      assertThat(user.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("[M-36] User.isAdmin returns true when role is ADMIN")
    void isAdmin_withAdmin_returnsTrue() {
      // given
      User user = baseUser(UserRole.ADMIN);

      // when / then
      assertThat(user.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("[M-37] User.isAdmin returns false when role is USER")
    void isAdmin_withUser_returnsFalse() {
      // given
      User user = baseUser(UserRole.USER);

      // when / then
      assertThat(user.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("[M-38] User.isAdmin returns false when role is TRAINER")
    void isAdmin_withTrainer_returnsFalse() {
      // given
      User user = baseUser(UserRole.TRAINER);

      // when / then
      assertThat(user.isAdmin()).isFalse();
    }
  }

  private User baseUser(UserRole role) {
    Instant now = Instant.parse("2026-02-28T18:00:00Z");
    return User.builder()
        .id(21L)
        .email("user@example.com")
        .nickname("tester")
        .role(role)
        .createdAt(now.minus(10, ChronoUnit.DAYS))
        .updatedAt(now.minus(1, ChronoUnit.DAYS))
        .build();
  }
}
