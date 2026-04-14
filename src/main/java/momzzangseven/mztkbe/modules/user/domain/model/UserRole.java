package momzzangseven.mztkbe.modules.user.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Domain Value Object representing user roles and permissions. */
@Getter
@RequiredArgsConstructor
public enum UserRole {
  /** Regular user role with basic permissions. */
  USER("ROLE_USER", "Regular User", 1),

  /** Regular user role with trainer permissions. */
  TRAINER("ROLE_TRAINER", "Trainer", 2),

  /** Administrator role — logical parent, never directly assigned to users. */
  ADMIN("ROLE_ADMIN", "Administrator", 90),

  /** Seed administrator created during bootstrap. */
  ADMIN_SEED("ROLE_ADMIN_SEED", "Seed Administrator", 99),

  /** Administrator created by another admin at runtime. */
  ADMIN_GENERATED("ROLE_ADMIN_GENERATED", "Generated Administrator", 99);

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
   * Check if this role is admin (ADMIN, ADMIN_SEED, or ADMIN_GENERATED).
   *
   * @return true if this role has admin-level privileges
   */
  public boolean isAdmin() {
    return this.level >= ADMIN.level;
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
