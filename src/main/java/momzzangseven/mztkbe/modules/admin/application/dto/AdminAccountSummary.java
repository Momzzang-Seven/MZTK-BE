package momzzangseven.mztkbe.modules.admin.application.dto;

import java.time.Instant;

/**
 * Summary of an admin account for listing purposes.
 *
 * @param userId the user ID
 * @param loginId the login ID
 * @param isSeed whether this is a seed admin
 * @param createdBy the user ID of the creator
 * @param lastLoginAt last login timestamp
 * @param passwordLastRotatedAt last password rotation timestamp
 */
public record AdminAccountSummary(
    Long userId,
    String loginId,
    boolean isSeed,
    Long createdBy,
    Instant lastLoginAt,
    Instant passwordLastRotatedAt) {}
