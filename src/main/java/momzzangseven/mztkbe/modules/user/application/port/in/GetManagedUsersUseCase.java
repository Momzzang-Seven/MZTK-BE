package momzzangseven.mztkbe.modules.user.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.user.application.dto.GetManagedUsersQuery;
import momzzangseven.mztkbe.modules.user.application.dto.ManagedUserView;

public interface GetManagedUsersUseCase {

  List<ManagedUserView> execute(GetManagedUsersQuery query);
}
