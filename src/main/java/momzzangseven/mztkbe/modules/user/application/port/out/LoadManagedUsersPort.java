package momzzangseven.mztkbe.modules.user.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.user.application.dto.GetManagedUsersQuery;
import momzzangseven.mztkbe.modules.user.application.dto.ManagedUserView;

/** Output port for the admin-facing managed-user list read model. */
public interface LoadManagedUsersPort {

  List<ManagedUserView> load(GetManagedUsersQuery query);
}
