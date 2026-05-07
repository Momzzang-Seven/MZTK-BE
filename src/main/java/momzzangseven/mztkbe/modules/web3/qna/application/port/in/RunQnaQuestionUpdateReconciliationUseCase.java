package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.RunQnaQuestionUpdateReconciliationCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.RunQnaQuestionUpdateReconciliationResult;

public interface RunQnaQuestionUpdateReconciliationUseCase {

  RunQnaQuestionUpdateReconciliationResult run(RunQnaQuestionUpdateReconciliationCommand command);
}
