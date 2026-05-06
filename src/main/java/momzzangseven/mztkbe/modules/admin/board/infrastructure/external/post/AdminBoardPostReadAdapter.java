package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.post;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostsPort;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostType;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsPageQuery;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsQuery;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostView;
import momzzangseven.mztkbe.modules.post.application.port.in.GetManagedBoardPostsUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBoardPostReadAdapter implements LoadAdminBoardPostsPort {

  private final GetManagedBoardPostsUseCase getManagedBoardPostsUseCase;

  @Override
  public List<AdminBoardPostView> load(AdminBoardPostQuery query) {
    return getManagedBoardPostsUseCase
        .execute(new GetManagedBoardPostsQuery(query.search(), toPostStatus(query.status())))
        .stream()
        .map(this::toAdminBoardPostView)
        .toList();
  }

  @Override
  public Page<AdminBoardPostView> loadPage(AdminBoardPostPageQuery query) {
    return getManagedBoardPostsUseCase
        .executePage(
            new GetManagedBoardPostsPageQuery(
                query.search(),
                toPostStatus(query.status()),
                query.page(),
                query.size(),
                query.sortKey().name()))
        .map(this::toAdminBoardPostView);
  }

  private AdminBoardPostView toAdminBoardPostView(ManagedBoardPostView post) {
    return new AdminBoardPostView(
        post.postId(),
        toAdminBoardPostType(post.type()),
        toAdminBoardPostStatus(post.status()),
        post.title(),
        post.content(),
        post.writerId(),
        post.createdAt());
  }

  private PostStatus toPostStatus(AdminBoardPostStatus status) {
    return status == null ? null : PostStatus.valueOf(status.name());
  }

  private AdminBoardPostType toAdminBoardPostType(PostType type) {
    return AdminBoardPostType.valueOf(type.name());
  }

  private AdminBoardPostStatus toAdminBoardPostStatus(PostStatus status) {
    return AdminBoardPostStatus.valueOf(status.name());
  }
}
