package momzzangseven.mztkbe.modules.user.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.user.application.dto.GetManagedUsersPageQuery;
import momzzangseven.mztkbe.modules.user.application.dto.GetManagedUsersQuery;
import momzzangseven.mztkbe.modules.user.application.dto.ManagedUserView;
import org.springframework.data.domain.Page;

public interface GetManagedUsersUseCase {

  List<ManagedUserView> execute(GetManagedUsersQuery query);

  Page<ManagedUserView> executePage(GetManagedUsersPageQuery query);
}
