package momzzangseven.mztkbe.modules.user.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.user.application.dto.GetManagedUsersQuery;
import momzzangseven.mztkbe.modules.user.application.dto.ManagedUserView;
import momzzangseven.mztkbe.modules.user.application.port.in.GetManagedUsersUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadManagedUsersPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for admin-facing user-management reads. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetManagedUsersService implements GetManagedUsersUseCase {

  private final LoadManagedUsersPort loadManagedUsersPort;

  @Override
  public List<ManagedUserView> execute(GetManagedUsersQuery query) {
    return loadManagedUsersPort.load(query);
  }
}
