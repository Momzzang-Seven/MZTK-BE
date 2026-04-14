package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.RecoverQuestionPostEscrowCommand;
import momzzangseven.mztkbe.modules.post.application.dto.PostMutationResult;

/** Recreates the on-chain create intent for a local question post that never got projected. */
public interface RecoverQuestionPostEscrowUseCase {

  PostMutationResult recoverQuestionCreate(RecoverQuestionPostEscrowCommand command);
}
