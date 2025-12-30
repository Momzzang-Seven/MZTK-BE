package momzzangseven.mztkbe.modules.user.api.dto;

import jakarta.validation.constraints.NotNull;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/** Request DTO for updating user role. */
public record UpdateUserRoleRequestDTO(@NotNull(message = "Role is required") UserRole role) {}
