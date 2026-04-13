package momzzangseven.mztkbe.modules.admin.application.dto;

import java.time.Instant;

/**
 * Result of creating a new admin account.
 *
 * @param userId the created user's ID
 * @param loginId the generated login ID
 * @param plaintext the generated plaintext password (displayed once)
 * @param createdAt creation timestamp
 */
public record CreateAdminAccountResult(
    Long userId, String loginId, String plaintext, Instant createdAt) {

  @Override
  public String toString() {
    return "CreateAdminAccountResult[userId="
        + userId
        + ", loginId="
        + loginId
        + ", plaintext=***, createdAt="
        + createdAt
        + "]";
  }
}
