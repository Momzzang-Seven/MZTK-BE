package momzzangseven.mztkbe.modules.admin.user.infrastructure.external.comment;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserCommentCountsPort;
import momzzangseven.mztkbe.modules.comment.application.port.in.CountCommentsUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminUserCommentCountAdapter implements LoadAdminUserCommentCountsPort {

  private final CountCommentsUseCase countCommentsUseCase;

  @Override
  public Map<Long, Long> load(List<Long> userIds) {
    return countCommentsUseCase.countCommentsByUserIds(userIds);
  }
}
