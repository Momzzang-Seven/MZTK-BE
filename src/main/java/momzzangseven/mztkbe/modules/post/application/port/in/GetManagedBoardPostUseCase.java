package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostTargetView;

/** Input port for loading one post target for admin board moderation. */
public interface GetManagedBoardPostUseCase {

  ManagedBoardPostTargetView execute(Long postId);
}
