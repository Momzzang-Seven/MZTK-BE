package momzzangseven.mztkbe.modules.user.application.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/** User-management row exposed to admin read models. */
public record ManagedUserView(
    Long userId, String nickname, UserRole role, String email, Instant joinedAt) {}
