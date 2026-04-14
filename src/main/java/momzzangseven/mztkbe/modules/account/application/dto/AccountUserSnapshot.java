package momzzangseven.mztkbe.modules.account.application.dto;

/**
 * Account-internal snapshot of user profile data. Uses String for role to avoid depending on
 * user-module's UserRole enum. Status is intentionally excluded — check via {@code
 * UserAccount.isActive()} / {@code UserAccount.isDeleted()}.
 */
public record AccountUserSnapshot(
    Long userId, String email, String nickname, String profileImageUrl, String role) {}
