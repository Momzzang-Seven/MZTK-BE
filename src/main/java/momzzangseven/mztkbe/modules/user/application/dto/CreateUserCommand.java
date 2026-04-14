package momzzangseven.mztkbe.modules.user.application.dto;

/**
 * Command for creating a new user. The {@code role} field is a plain String so that the calling
 * module (e.g. account) does not need to depend on the {@code UserRole} enum.
 */
public record CreateUserCommand(
    String email, String nickname, String profileImageUrl, String role) {}
