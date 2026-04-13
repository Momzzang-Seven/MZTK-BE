package momzzangseven.mztkbe.modules.admin.api.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.admin.application.dto.CreateAdminAccountResult;

/** Response DTO for admin account creation. */
public record CreateAdminAccountResponseDTO(
    Long userId, String loginId, String generatedPassword, Instant createdAt) {

  public static CreateAdminAccountResponseDTO from(CreateAdminAccountResult result) {
    return new CreateAdminAccountResponseDTO(
        result.userId(), result.loginId(), result.plaintext(), result.createdAt());
  }
}
