package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostResult;

/**
 * Internal admin-board entry point for post moderation changes whose audit is recorded by the
 * caller.
 */
public interface ModerateManagedPostUseCase {

  ModeratePostResult blockManagedPost(ModeratePostCommand command);

  ModeratePostResult unblockManagedPost(ModeratePostCommand command);
}
