package momzzangseven.mztkbe.modules.user.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Domain Value Object representing user roles and permissions. */
@Getter
@RequiredArgsConstructor
public enum UserRole {
  /**
   * Regular user role with basic permissions.
   */
  USER("ROLE_USER", "Regular User", 1),

  /**
   * Administrator role with full permissions.
   */
  ADMIN("ROLE_ADMIN", "Administrator", 99);

  private final String authority;
  private final String displayName;
  private final int level;

  /**
   * Check if this role has higher or equal privilege than the given role.
   *
   * @param other the role to compare with
   * @return true if this role has higher or equal level
   */
  public boolean hasHigherOrEqualPrivilegeThan(UserRole other) {
    return this.level >= other.level;
  }

  /**
   * Check if this role is admin.
   *
   * @return true if this role is ADMIN
   */
  public boolean isAdmin() {
    return this == ADMIN;
  }

  /**
   * Get the role name without the ROLE_ prefix.
   *
   * @return role name (e.g., "USER", "ADMIN")
   */
  public String getRoleName() {
    return this.name();
  }

  /**
   * Parse authority string to UserRole enum.
   *
   * @param authority authority string (e.g., "ROLE_USER")
   * @return corresponding UserRole
   * @throws IllegalArgumentException if authority is not recognized
   */
  public static UserRole fromAuthority(String authority) {
    for (UserRole role : values()) {
      if (role.authority.equals(authority)) {
        return role;
      }
    }
    throw new IllegalArgumentException("Unknown authority: " + authority);
  }
}
