package momzzangseven.mztkbe.modules.comment.application.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.port.in.CountCommentsUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentCountService implements CountCommentsUseCase {

  private final LoadCommentPort loadCommentPort;

  @Override
  public Map<Long, Long> countCommentsByPostIds(List<Long> postIds) {
    return loadCommentPort.countCommentsByPostIds(postIds);
  }

  @Override
  public long countCommentsByPostId(Long postId) {
    return loadCommentPort.countCommentsByPostId(postId);
  }
}
