package momzzangseven.mztkbe.modules.account.application.port.in;

import java.util.Map;
import momzzangseven.mztkbe.modules.account.application.dto.GetManagedUserAccountStatusesQuery;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;

public interface GetManagedUserAccountStatusesUseCase {

  Map<Long, AccountStatus> execute(GetManagedUserAccountStatusesQuery query);
}
