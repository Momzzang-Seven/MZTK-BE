package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.comment;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostCommentCountsPort;
import momzzangseven.mztkbe.modules.comment.application.port.in.CountManagedBoardPostCommentsUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBoardCommentCountAdapter implements LoadAdminBoardPostCommentCountsPort {

  private final CountManagedBoardPostCommentsUseCase countManagedBoardPostCommentsUseCase;

  @Override
  public Map<Long, Long> load(List<Long> postIds) {
    return countManagedBoardPostCommentsUseCase.countByPostIds(postIds);
  }
}
