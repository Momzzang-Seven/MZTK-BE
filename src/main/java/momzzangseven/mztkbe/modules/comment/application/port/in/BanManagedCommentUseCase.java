package momzzangseven.mztkbe.modules.comment.application.port.in;

import momzzangseven.mztkbe.modules.comment.application.dto.BanManagedCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.BanManagedCommentResult;

/** Input port for admin-managed comment soft delete. */
public interface BanManagedCommentUseCase {

  BanManagedCommentResult execute(BanManagedCommentCommand command);
}
