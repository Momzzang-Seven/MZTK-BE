package momzzangseven.mztkbe.modules.comment.application.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.port.in.CountManagedBoardPostCommentsUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managed board comment counts. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagedBoardCommentCountService implements CountManagedBoardPostCommentsUseCase {

  private final LoadCommentPort loadCommentPort;

  @Override
  public Map<Long, Long> countByPostIds(List<Long> postIds) {
    return loadCommentPort.countManagedBoardCommentsByPostIds(postIds);
  }
}
