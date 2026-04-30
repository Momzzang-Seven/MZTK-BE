package momzzangseven.mztkbe.modules.post.application.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.port.in.GetMyPostsCursorUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMyPostsCursorService implements GetMyPostsCursorUseCase {

  private final PostPersistencePort postPersistencePort;
  private final LoadTagPort loadTagPort;
  private final PostListEnricher postListEnricher;

  @Override
  public GetMyPostsCursorResult execute(GetMyPostsCursorCommand command) {
    command.validate();
    CursorPageRequest pageRequest = command.pageRequest();

    Optional<Long> tagId = Optional.empty();
    if (StringUtils.hasText(command.tagName())) {
      tagId = loadTagPort.findTagIdByName(command.tagName());
      if (tagId.isEmpty()) {
        return new GetMyPostsCursorResult(List.of(), false, null);
      }
    }

    List<Post> posts =
        postPersistencePort.findPostsByAuthorCursor(
            command.requesterId(),
            command.type(),
            tagId.orElse(null),
            command.effectiveSearch(),
            pageRequest);
    boolean hasNext = posts.size() > pageRequest.size();
    List<Post> pagePosts = hasNext ? posts.subList(0, pageRequest.size()) : posts;
    if (pagePosts.isEmpty()) {
      return new GetMyPostsCursorResult(List.of(), false, null);
    }

    List<PostListResult> enrichedPosts = postListEnricher.enrich(pagePosts, command.requesterId());
    String nextCursor =
        hasNext ? createNextCursor(pagePosts.get(pagePosts.size() - 1), pageRequest) : null;
    return new GetMyPostsCursorResult(enrichedPosts, hasNext, nextCursor);
  }

  private String createNextCursor(Post lastPost, CursorPageRequest pageRequest) {
    return CursorCodec.encode(
        new KeysetCursor(lastPost.getCreatedAt(), lastPost.getId(), pageRequest.scope()));
  }
}
