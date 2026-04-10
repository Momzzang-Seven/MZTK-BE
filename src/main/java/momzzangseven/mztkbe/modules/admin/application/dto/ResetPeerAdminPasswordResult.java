package momzzangseven.mztkbe.modules.admin.application.dto;

import java.time.Instant;

/**
 * Result of a peer admin password reset.
 *
 * @param userId the target admin's user ID
 * @param loginId the target admin's login ID
 * @param plaintext the newly generated plaintext password (displayed once)
 * @param resetAt reset timestamp
 */
public record ResetPeerAdminPasswordResult(
    Long userId, String loginId, String plaintext, Instant resetAt) {

  @Override
  public String toString() {
    return "ResetPeerAdminPasswordResult[userId="
        + userId
        + ", loginId="
        + loginId
        + ", plaintext=***, resetAt="
        + resetAt
        + "]";
  }
}
