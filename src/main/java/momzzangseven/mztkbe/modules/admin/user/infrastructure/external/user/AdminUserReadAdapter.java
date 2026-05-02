package momzzangseven.mztkbe.modules.admin.user.infrastructure.external.user;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUsersPort;
import momzzangseven.mztkbe.modules.user.application.dto.GetManagedUsersQuery;
import momzzangseven.mztkbe.modules.user.application.port.in.GetManagedUsersUseCase;
import org.springframework.stereotype.Component;

/** Cross-module adapter exposing user-management read models to the admin module. */
@Component
@RequiredArgsConstructor
public class AdminUserReadAdapter implements LoadAdminUsersPort {

  private final GetManagedUsersUseCase getManagedUsersUseCase;

  @Override
  public java.util.List<AdminUserProfileView> load(AdminUserProfileQuery query) {
    return getManagedUsersUseCase
        .execute(
            new GetManagedUsersQuery(
                query.search(),
                query.role() != null ? query.role().toUserRole() : null,
                query.candidateUserIds()))
        .stream()
        .map(
            item ->
                new AdminUserProfileView(
                    item.userId(), item.nickname(), item.role(), item.email(), item.joinedAt()))
        .toList();
  }
}
