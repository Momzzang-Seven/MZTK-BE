package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;

public interface ExecuteQnaAdminSettlementUseCase {

  QnaExecutionIntentResult execute(ExecuteQnaAdminSettlementCommand command);
}
