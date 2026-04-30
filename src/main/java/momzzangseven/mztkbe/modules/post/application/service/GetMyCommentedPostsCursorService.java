package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyCommentedPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyCommentedPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.port.in.GetMyCommentedPostsCursorUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.CommentedPostRef;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadCommentedPostRefsPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMyCommentedPostsCursorService implements GetMyCommentedPostsCursorUseCase {

  private final LoadCommentedPostRefsPort loadCommentedPostRefsPort;
  private final PostPersistencePort postPersistencePort;
  private final PostListEnricher postListEnricher;

  @Override
  public GetMyCommentedPostsCursorResult execute(GetMyCommentedPostsCursorCommand command) {
    command.validate();
    CursorPageRequest pageRequest = command.pageRequest();
    List<CommentedPostRef> refs =
        loadCommentedPostRefsPort.loadCommentedPostRefs(
            command.requesterId(), command.type(), command.effectiveSearch(), pageRequest);
    boolean hasNext = refs.size() > pageRequest.size();
    List<CommentedPostRef> pageRefs = hasNext ? refs.subList(0, pageRequest.size()) : refs;
    if (pageRefs.isEmpty()) {
      return new GetMyCommentedPostsCursorResult(List.of(), false, null);
    }

    List<Long> postIds = pageRefs.stream().map(CommentedPostRef::postId).toList();
    List<Post> posts = postPersistencePort.loadPostsByIdsPreservingOrder(postIds);
    List<PostListResult> results = postListEnricher.enrich(posts, command.requesterId());
    String nextCursor =
        hasNext ? createNextCursor(pageRefs.get(pageRefs.size() - 1), pageRequest) : null;

    return new GetMyCommentedPostsCursorResult(results, hasNext, nextCursor);
  }

  private String createNextCursor(CommentedPostRef ref, CursorPageRequest pageRequest) {
    return CursorCodec.encode(
        new KeysetCursor(ref.latestCommentedAt(), ref.latestCommentId(), pageRequest.scope()));
  }
}
