package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.post;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.ChangeAdminBoardPostModerationPort;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostResult;
import momzzangseven.mztkbe.modules.post.application.port.in.BlockPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UnblockPostUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBoardPostModerationAdapter implements ChangeAdminBoardPostModerationPort {

  private final BlockPostUseCase blockPostUseCase;
  private final UnblockPostUseCase unblockPostUseCase;

  @Override
  public AdminBoardPostModerationChangeResult block(Long operatorUserId, Long postId) {
    return toResult(blockPostUseCase.blockPost(new ModeratePostCommand(operatorUserId, postId)));
  }

  @Override
  public AdminBoardPostModerationChangeResult unblock(Long operatorUserId, Long postId) {
    return toResult(
        unblockPostUseCase.unblockPost(new ModeratePostCommand(operatorUserId, postId)));
  }

  private AdminBoardPostModerationChangeResult toResult(ModeratePostResult result) {
    return new AdminBoardPostModerationChangeResult(
        result.postId(),
        result.moderated(),
        AdminBoardPostPublicationStatus.valueOf(result.publicationStatus().name()),
        AdminBoardPostModerationStatus.valueOf(result.moderationStatus().name()));
  }
}
