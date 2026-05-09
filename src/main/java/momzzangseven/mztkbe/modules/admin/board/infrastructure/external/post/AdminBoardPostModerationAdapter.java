package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.post;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.ChangeAdminBoardPostModerationPort;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostResult;
import momzzangseven.mztkbe.modules.post.application.port.in.ModerateManagedPostUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBoardPostModerationAdapter implements ChangeAdminBoardPostModerationPort {

  private final ModerateManagedPostUseCase moderateManagedPostUseCase;

  @Override
  public AdminBoardPostModerationChangeResult block(Long operatorUserId, Long postId) {
    return toResult(
        moderateManagedPostUseCase.blockManagedPost(
            new ModeratePostCommand(operatorUserId, postId)));
  }

  @Override
  public AdminBoardPostModerationChangeResult unblock(Long operatorUserId, Long postId) {
    return toResult(
        moderateManagedPostUseCase.unblockManagedPost(
            new ModeratePostCommand(operatorUserId, postId)));
  }

  private AdminBoardPostModerationChangeResult toResult(ModeratePostResult result) {
    return new AdminBoardPostModerationChangeResult(
        result.postId(),
        result.moderated(),
        AdminBoardPostEnumMapper.toAdminPublicationStatus(result.publicationStatus()),
        AdminBoardPostEnumMapper.toAdminModerationStatus(result.moderationStatus()),
        result.publiclyVisible());
  }
}
