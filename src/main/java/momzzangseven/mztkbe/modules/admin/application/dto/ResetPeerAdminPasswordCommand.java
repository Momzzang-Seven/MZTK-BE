package momzzangseven.mztkbe.modules.admin.application.dto;

/**
 * Command for resetting another admin's password (peer-reset).
 *
 * @param operatorUserId the admin performing the reset
 * @param targetUserId the admin whose password is being reset
 */
public record ResetPeerAdminPasswordCommand(Long operatorUserId, Long targetUserId) {}
