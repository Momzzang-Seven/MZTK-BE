package momzzangseven.mztkbe.modules.post.infrastructure.external.comment.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.modules.comment.application.dto.FindCommentedPostRefsQuery;
import momzzangseven.mztkbe.modules.comment.application.port.in.FindCommentedPostRefsUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.CommentedPostRef;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadCommentedPostRefsPort;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentedPostRefsAdapter implements LoadCommentedPostRefsPort {

  private final FindCommentedPostRefsUseCase findCommentedPostRefsUseCase;

  @Override
  public List<CommentedPostRef> loadCommentedPostRefs(
      Long requesterId, PostType type, CursorPageRequest pageRequest) {
    return findCommentedPostRefsUseCase
        .execute(new FindCommentedPostRefsQuery(requesterId, type.name(), pageRequest))
        .stream()
        .map(
            ref ->
                new CommentedPostRef(ref.postId(), ref.latestCommentId(), ref.latestCommentedAt()))
        .toList();
  }
}
