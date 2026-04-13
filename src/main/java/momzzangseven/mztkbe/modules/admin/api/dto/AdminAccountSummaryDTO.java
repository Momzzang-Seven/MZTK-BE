package momzzangseven.mztkbe.modules.admin.api.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.admin.application.dto.AdminAccountSummary;

/** Response DTO for a single admin account summary. */
public record AdminAccountSummaryDTO(
    Long userId,
    String loginId,
    boolean isSeed,
    Long createdBy,
    Instant lastLoginAt,
    Instant passwordLastRotatedAt) {

  public static AdminAccountSummaryDTO from(AdminAccountSummary summary) {
    return new AdminAccountSummaryDTO(
        summary.userId(),
        summary.loginId(),
        summary.isSeed(),
        summary.createdBy(),
        summary.lastLoginAt(),
        summary.passwordLastRotatedAt());
  }
}
