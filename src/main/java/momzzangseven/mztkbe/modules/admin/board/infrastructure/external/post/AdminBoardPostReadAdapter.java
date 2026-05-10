package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.post;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostsPort;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsPageQuery;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsQuery;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostView;
import momzzangseven.mztkbe.modules.post.application.port.in.GetManagedBoardPostsUseCase;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBoardPostReadAdapter implements LoadAdminBoardPostsPort {

  private final GetManagedBoardPostsUseCase getManagedBoardPostsUseCase;

  @Override
  public List<AdminBoardPostView> load(AdminBoardPostQuery query) {
    return getManagedBoardPostsUseCase
        .execute(
            new GetManagedBoardPostsQuery(
                query.search(),
                AdminBoardPostEnumMapper.toPostStatus(query.status()),
                AdminBoardPostEnumMapper.toPostType(query.type()),
                AdminBoardPostEnumMapper.toPostPublicationStatus(query.publicationStatus()),
                AdminBoardPostEnumMapper.toPostModerationStatus(query.moderationStatus())))
        .stream()
        .map(this::toAdminBoardPostView)
        .toList();
  }

  @Override
  public long count(AdminBoardPostQuery query) {
    return getManagedBoardPostsUseCase.count(
        new GetManagedBoardPostsQuery(
            query.search(),
            AdminBoardPostEnumMapper.toPostStatus(query.status()),
            AdminBoardPostEnumMapper.toPostType(query.type()),
            AdminBoardPostEnumMapper.toPostPublicationStatus(query.publicationStatus()),
            AdminBoardPostEnumMapper.toPostModerationStatus(query.moderationStatus())));
  }

  @Override
  public Page<AdminBoardPostView> loadPage(AdminBoardPostPageQuery query) {
    return getManagedBoardPostsUseCase
        .executePage(
            new GetManagedBoardPostsPageQuery(
                query.search(),
                AdminBoardPostEnumMapper.toPostStatus(query.status()),
                AdminBoardPostEnumMapper.toPostType(query.type()),
                AdminBoardPostEnumMapper.toPostPublicationStatus(query.publicationStatus()),
                AdminBoardPostEnumMapper.toPostModerationStatus(query.moderationStatus()),
                query.page(),
                query.size(),
                query.sortKey().name()))
        .map(this::toAdminBoardPostView);
  }

  private AdminBoardPostView toAdminBoardPostView(ManagedBoardPostView post) {
    return new AdminBoardPostView(
        post.postId(),
        AdminBoardPostEnumMapper.toAdminPostType(post.type()),
        AdminBoardPostEnumMapper.toAdminPostStatus(post.status()),
        AdminBoardPostEnumMapper.toAdminPublicationStatus(post.publicationStatus()),
        AdminBoardPostEnumMapper.toAdminModerationStatus(post.moderationStatus()),
        post.title(),
        post.content(),
        post.writerId(),
        post.createdAt());
  }
}
