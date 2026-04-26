package momzzangseven.mztkbe.modules.comment.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentedPostRef;
import momzzangseven.mztkbe.modules.comment.application.dto.FindCommentedPostRefsQuery;
import momzzangseven.mztkbe.modules.comment.application.port.in.FindCommentedPostRefsUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentedPostRefService implements FindCommentedPostRefsUseCase {

  private final LoadCommentPort loadCommentPort;

  @Override
  public List<CommentedPostRef> execute(FindCommentedPostRefsQuery query) {
    query.validate();
    return loadCommentPort.findCommentedPostRefsByUserCursor(query);
  }
}
