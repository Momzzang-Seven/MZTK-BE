package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.post;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostsPort;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsQuery;
import momzzangseven.mztkbe.modules.post.application.port.in.GetManagedBoardPostsUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBoardPostReadAdapter implements LoadAdminBoardPostsPort {

  private final GetManagedBoardPostsUseCase getManagedBoardPostsUseCase;

  @Override
  public List<AdminBoardPostView> load(AdminBoardPostQuery query) {
    return getManagedBoardPostsUseCase
        .execute(new GetManagedBoardPostsQuery(query.search(), query.status()))
        .stream()
        .map(
            post ->
                new AdminBoardPostView(
                    post.postId(),
                    post.type(),
                    post.status(),
                    post.title(),
                    post.content(),
                    post.writerId(),
                    post.createdAt()))
        .toList();
  }
}
