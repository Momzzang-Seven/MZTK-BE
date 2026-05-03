package momzzangseven.mztkbe.modules.user.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.user.application.dto.GetManagedUsersPageQuery;
import momzzangseven.mztkbe.modules.user.application.dto.GetManagedUsersQuery;
import momzzangseven.mztkbe.modules.user.application.dto.ManagedUserView;
import org.springframework.data.domain.Page;

/** Output port for the admin-facing managed-user list read model. */
public interface LoadManagedUsersPort {

  List<ManagedUserView> load(GetManagedUsersQuery query);

  Page<ManagedUserView> loadPage(GetManagedUsersPageQuery query);
}
