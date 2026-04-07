package momzzangseven.mztkbe.modules.user.application.dto;

/** Command for updating a user's profile (nickname and/or profile image). */
public record UpdateUserProfileCommand(Long userId, String nickname, String profileImageUrl) {}
