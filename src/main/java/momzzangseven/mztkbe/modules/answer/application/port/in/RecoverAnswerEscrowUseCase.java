package momzzangseven.mztkbe.modules.answer.application.port.in;

import momzzangseven.mztkbe.modules.answer.application.dto.AnswerMutationResult;
import momzzangseven.mztkbe.modules.answer.application.dto.RecoverAnswerEscrowCommand;

/** Recreates the on-chain create intent for a local answer that never got projected. */
public interface RecoverAnswerEscrowUseCase {

  AnswerMutationResult recoverAnswerCreate(RecoverAnswerEscrowCommand command);
}
