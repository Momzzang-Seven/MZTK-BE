package momzzangseven.mztkbe.modules.user.application.dto;

import java.util.Set;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.lang.Nullable;

/** Query for the admin-facing managed-user list read model. */
public record GetManagedUsersQuery(
    String search, UserRole role, @Nullable Set<Long> candidateUserIds) {

  public GetManagedUsersQuery {
    if (candidateUserIds != null) {
      candidateUserIds = Set.copyOf(candidateUserIds);
    }
  }
}
