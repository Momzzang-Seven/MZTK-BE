package momzzangseven.mztkbe.modules.admin.application.dto;

/**
 * Result of a successful admin local authentication. Contains the linked user ID so the caller can
 * resolve the full user profile.
 *
 * @param userId the user ID linked to the authenticated admin account
 */
public record AuthenticateAdminLocalResult(Long userId) {}
