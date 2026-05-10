package momzzangseven.mztkbe.modules.user.application.dto;

import java.util.Set;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.lang.Nullable;

/** Paged query for the admin-facing managed-user list read model. */
public record GetManagedUsersPageQuery(
    String search,
    UserRole role,
    @Nullable Set<Long> candidateUserIds,
    int page,
    int size,
    String sortKey) {

  public GetManagedUsersPageQuery {
    if (candidateUserIds != null) {
      candidateUserIds = Set.copyOf(candidateUserIds);
    }
    if (page < 0) {
      throw new IllegalArgumentException("page must be zero or positive");
    }
    if (size <= 0) {
      throw new IllegalArgumentException("size must be positive");
    }
    if (sortKey == null || sortKey.isBlank()) {
      throw new IllegalArgumentException("sortKey is required");
    }
  }
}
