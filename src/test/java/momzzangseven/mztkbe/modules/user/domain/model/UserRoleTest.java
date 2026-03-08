package momzzangseven.mztkbe.modules.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserRole unit test")
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
}
