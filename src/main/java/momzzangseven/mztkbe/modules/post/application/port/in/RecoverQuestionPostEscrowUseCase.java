package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.PostMutationResult;
import momzzangseven.mztkbe.modules.post.application.dto.RecoverQuestionPostEscrowCommand;

/** Recreates the on-chain create intent for a local question post that never got projected. */
public interface RecoverQuestionPostEscrowUseCase {

  PostMutationResult recoverQuestionCreate(RecoverQuestionPostEscrowCommand command);
}
