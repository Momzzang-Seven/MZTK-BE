package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyLikedPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyLikedPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.port.in.GetMyLikedPostsCursorUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LikedPostRow;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMyLikedPostsCursorService implements GetMyLikedPostsCursorUseCase {

  private final PostLikePersistencePort postLikePersistencePort;
  private final PostListEnricher postListEnricher;

  @Override
  public GetMyLikedPostsCursorResult execute(GetMyLikedPostsCursorCommand command) {
    command.validate();
    CursorPageRequest pageRequest = command.pageRequest();

    List<LikedPostRow> rows =
        postLikePersistencePort.findLikedPostsByCursor(
            command.requesterId(), command.type(), pageRequest);
    boolean hasNext = rows.size() > pageRequest.size();
    List<LikedPostRow> pageRows = hasNext ? rows.subList(0, pageRequest.size()) : rows;
    if (pageRows.isEmpty()) {
      return new GetMyLikedPostsCursorResult(List.of(), false, null);
    }

    List<Post> posts = pageRows.stream().map(LikedPostRow::post).toList();
    List<PostListResult> enrichedPosts = postListEnricher.enrichAllLiked(posts);
    String nextCursor =
        hasNext ? createNextCursor(pageRows.get(pageRows.size() - 1), pageRequest) : null;
    return new GetMyLikedPostsCursorResult(enrichedPosts, hasNext, nextCursor);
  }

  private String createNextCursor(LikedPostRow lastRow, CursorPageRequest pageRequest) {
    return CursorCodec.encode(
        new KeysetCursor(lastRow.likedAt(), lastRow.likeId(), pageRequest.scope()));
  }
}
