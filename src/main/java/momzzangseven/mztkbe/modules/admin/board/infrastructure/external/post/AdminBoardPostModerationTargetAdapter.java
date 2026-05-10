package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.post;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostModerationTargetPort;
import momzzangseven.mztkbe.modules.post.application.port.in.GetManagedBoardPostUseCase;
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
        post.postId(),
        AdminBoardPostEnumMapper.toAdminBoardType(post.type()),
        AdminBoardPostEnumMapper.toAdminPostStatus(post.status()));
  }
}
