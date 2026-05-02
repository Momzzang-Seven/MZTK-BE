package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.post;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostModerationTargetPort;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
import momzzangseven.mztkbe.modules.post.application.port.in.GetManagedBoardPostUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBoardPostModerationTargetAdapter
    implements LoadAdminBoardPostModerationTargetPort {

  private final GetManagedBoardPostUseCase getManagedBoardPostUseCase;

  @Override
  public AdminBoardPostModerationTarget load(Long postId) {
    var post = getManagedBoardPostUseCase.execute(postId);
    return new AdminBoardPostModerationTarget(
        post.postId(), toBoardType(post.type()), post.status());
  }

  private AdminBoardType toBoardType(PostType postType) {
    return switch (postType) {
      case FREE -> AdminBoardType.FREE;
      case QUESTION -> AdminBoardType.QUESTION;
    };
  }
}
