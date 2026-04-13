package momzzangseven.mztkbe.modules.admin.api.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.admin.application.dto.ResetPeerAdminPasswordResult;

/** Response DTO for peer admin password reset. */
public record ResetPeerAdminPasswordResponseDTO(
    Long userId, String loginId, String generatedPassword, Instant resetAt) {

  public static ResetPeerAdminPasswordResponseDTO from(ResetPeerAdminPasswordResult result) {
    return new ResetPeerAdminPasswordResponseDTO(
        result.userId(), result.loginId(), result.plaintext(), result.resetAt());
  }
}
