package momzzangseven.mztkbe.modules.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UserRole 단위 테스트")
class UserRoleTest {

  @Test
  @DisplayName("fromAuthority resolves known authority")
  void fromAuthority_knownAuthority_returnsRole() {
    assertThat(UserRole.fromAuthority("ROLE_USER")).isEqualTo(UserRole.USER);
    assertThat(UserRole.fromAuthority("ROLE_TRAINER")).isEqualTo(UserRole.TRAINER);
    assertThat(UserRole.fromAuthority("ROLE_ADMIN")).isEqualTo(UserRole.ADMIN);
  }

  @Test
  @DisplayName("fromAuthority rejects unknown authority")
  void fromAuthority_unknownAuthority_throwsException() {
    assertThatThrownBy(() -> UserRole.fromAuthority("ROLE_UNKNOWN"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown authority");
  }

  @Test
  @DisplayName("privilege comparison follows level order")
  void hasHigherOrEqualPrivilegeThan_comparesByLevel() {
    assertThat(UserRole.ADMIN.hasHigherOrEqualPrivilegeThan(UserRole.TRAINER)).isTrue();
    assertThat(UserRole.TRAINER.hasHigherOrEqualPrivilegeThan(UserRole.USER)).isTrue();
    assertThat(UserRole.USER.hasHigherOrEqualPrivilegeThan(UserRole.ADMIN)).isFalse();
  }

  @Test
  @DisplayName("isAdmin returns true only for ADMIN")
  void isAdmin_onlyAdminIsTrue() {
    assertThat(UserRole.ADMIN.isAdmin()).isTrue();
    assertThat(UserRole.USER.isAdmin()).isFalse();
    assertThat(UserRole.TRAINER.isAdmin()).isFalse();
  }

  @Test
  @DisplayName("getRoleName returns enum name")
  void getRoleName_returnsName() {
    assertThat(UserRole.USER.getRoleName()).isEqualTo("USER");
    assertThat(UserRole.TRAINER.getRoleName()).isEqualTo("TRAINER");
  }

  // ══════════════════════════════════════════════════════════════
  // Commit 1 — MOM-330: Admin role hierarchy tests
  // ══════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("isAdmin 검증")
  class IsAdminValidation {

    @Test
    @DisplayName("[M-1] isAdmin returns true for ADMIN")
    void isAdmin_admin_returnsTrue() {
      assertThat(UserRole.ADMIN.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("[M-2] isAdmin returns true for ADMIN_SEED")
    void isAdmin_adminSeed_returnsTrue() {
      assertThat(UserRole.ADMIN_SEED.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("[M-3] isAdmin returns true for ADMIN_GENERATED")
    void isAdmin_adminGenerated_returnsTrue() {
      assertThat(UserRole.ADMIN_GENERATED.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("[M-4] isAdmin returns false for USER")
    void isAdmin_user_returnsFalse() {
      assertThat(UserRole.USER.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("[M-5] isAdmin returns false for TRAINER")
    void isAdmin_trainer_returnsFalse() {
      assertThat(UserRole.TRAINER.isAdmin()).isFalse();
    }
  }

  @Nested
  @DisplayName("fromAuthority — 새 권한 문자열 검증")
  class FromAuthorityNewRoles {

    @Test
    @DisplayName("[M-6] fromAuthority resolves ADMIN_SEED authority")
    void fromAuthority_adminSeed_returnsAdminSeed() {
      assertThat(UserRole.fromAuthority("ROLE_ADMIN_SEED")).isEqualTo(UserRole.ADMIN_SEED);
    }

    @Test
    @DisplayName("[M-7] fromAuthority resolves ADMIN_GENERATED authority")
    void fromAuthority_adminGenerated_returnsAdminGenerated() {
      assertThat(UserRole.fromAuthority("ROLE_ADMIN_GENERATED"))
          .isEqualTo(UserRole.ADMIN_GENERATED);
    }
  }

  @Nested
  @DisplayName("hasHigherOrEqualPrivilegeThan — 관리자 계층 검증")
  class PrivilegeHierarchy {

    @Test
    @DisplayName("[M-8] ADMIN_SEED has higher privilege than ADMIN")
    void hasHigherOrEqualPrivilegeThan_adminSeedVsAdmin_returnsTrue() {
      assertThat(UserRole.ADMIN_SEED.hasHigherOrEqualPrivilegeThan(UserRole.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("[M-9] ADMIN_GENERATED has higher privilege than ADMIN")
    void hasHigherOrEqualPrivilegeThan_adminGeneratedVsAdmin_returnsTrue() {
      assertThat(UserRole.ADMIN_GENERATED.hasHigherOrEqualPrivilegeThan(UserRole.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("[M-10] ADMIN_SEED and ADMIN_GENERATED are equal in privilege")
    void hasHigherOrEqualPrivilegeThan_seedAndGenerated_bothTrue() {
      assertThat(UserRole.ADMIN_SEED.hasHigherOrEqualPrivilegeThan(UserRole.ADMIN_GENERATED))
          .isTrue();
      assertThat(UserRole.ADMIN_GENERATED.hasHigherOrEqualPrivilegeThan(UserRole.ADMIN_SEED))
          .isTrue();
    }

    @Test
    @DisplayName("[M-11] ADMIN does not outrank ADMIN_SEED")
    void hasHigherOrEqualPrivilegeThan_adminVsAdminSeed_returnsFalse() {
      assertThat(UserRole.ADMIN.hasHigherOrEqualPrivilegeThan(UserRole.ADMIN_SEED)).isFalse();
    }
  }

  @Nested
  @DisplayName("getRoleName — 새 열거형 값 검증")
  class GetRoleNameNewValues {

    @Test
    @DisplayName("[M-12] getRoleName returns correct names for new enum values")
    void getRoleName_newValues_returnsCorrectNames() {
      assertThat(UserRole.ADMIN_SEED.getRoleName()).isEqualTo("ADMIN_SEED");
      assertThat(UserRole.ADMIN_GENERATED.getRoleName()).isEqualTo("ADMIN_GENERATED");
    }
  }

  @Nested
  @DisplayName("getLevel — 레벨 값 검증")
  class GetLevelValidation {

    @Test
    @DisplayName("[M-13] ADMIN level is 90")
    void getLevel_admin_returns90() {
      assertThat(UserRole.ADMIN.getLevel()).isEqualTo(90);
    }

    @Test
    @DisplayName("[M-14] ADMIN_SEED and ADMIN_GENERATED levels are 99")
    void getLevel_adminSeedAndGenerated_returns99() {
      assertThat(UserRole.ADMIN_SEED.getLevel()).isEqualTo(99);
      assertThat(UserRole.ADMIN_GENERATED.getLevel()).isEqualTo(99);
    }
  }

  @Nested
  @DisplayName("getAuthority — 권한 문자열 검증")
  class GetAuthorityValidation {

    @Test
    @DisplayName("[M-15] ADMIN_SEED authority string is ROLE_ADMIN_SEED")
    void getAuthority_adminSeed_returnsRoleAdminSeed() {
      assertThat(UserRole.ADMIN_SEED.getAuthority()).isEqualTo("ROLE_ADMIN_SEED");
    }

    @Test
    @DisplayName("[M-16] ADMIN_GENERATED authority string is ROLE_ADMIN_GENERATED")
    void getAuthority_adminGenerated_returnsRoleAdminGenerated() {
      assertThat(UserRole.ADMIN_GENERATED.getAuthority()).isEqualTo("ROLE_ADMIN_GENERATED");
    }
  }

  @Nested
  @DisplayName("getDisplayName — 표시 이름 검증")
  class GetDisplayNameValidation {

    @Test
    @DisplayName("[M-17] ADMIN_SEED display name is Seed Administrator")
    void getDisplayName_adminSeed_returnsSeedAdministrator() {
      assertThat(UserRole.ADMIN_SEED.getDisplayName()).isEqualTo("Seed Administrator");
    }

    @Test
    @DisplayName("[M-18] ADMIN_GENERATED display name is Generated Administrator")
    void getDisplayName_adminGenerated_returnsGeneratedAdministrator() {
      assertThat(UserRole.ADMIN_GENERATED.getDisplayName()).isEqualTo("Generated Administrator");
    }
  }

  @Nested
  @DisplayName("values — 열거형 전체 검증")
  class EnumValuesValidation {

    @Test
    @DisplayName("[M-19] Enum values() includes all five roles")
    void values_containsAllFiveRoles() {
      UserRole[] values = UserRole.values();

      assertThat(values).hasSize(5);
      assertThat(values)
          .containsExactly(
              UserRole.USER,
              UserRole.TRAINER,
              UserRole.ADMIN,
              UserRole.ADMIN_SEED,
              UserRole.ADMIN_GENERATED);
    }
  }
}
