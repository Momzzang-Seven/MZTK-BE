package momzzangseven.mztkbe.modules.admin.user.infrastructure.external.post;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserPostCountsPort;
import momzzangseven.mztkbe.modules.post.application.port.in.CountPostsByUserIdsUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminUserPostCountAdapter implements LoadAdminUserPostCountsPort {

  private final CountPostsByUserIdsUseCase countPostsByUserIdsUseCase;

  @Override
  public Map<Long, Long> load(List<Long> userIds) {
    return countPostsByUserIdsUseCase.execute(userIds);
  }
}
