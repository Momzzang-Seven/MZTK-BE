package momzzangseven.mztkbe.modules.account.application.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.dto.GetManagedUserAccountStatusesQuery;
import momzzangseven.mztkbe.modules.account.application.port.in.GetManagedUserAccountStatusesUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadManagedUserAccountStatusesPort;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetManagedUserAccountStatusesService implements GetManagedUserAccountStatusesUseCase {

  private final LoadManagedUserAccountStatusesPort loadManagedUserAccountStatusesPort;

  @Override
  public Map<Long, AccountStatus> execute(GetManagedUserAccountStatusesQuery query) {
    return loadManagedUserAccountStatusesPort.load(query.userIds(), query.statusFilter());
  }
}
