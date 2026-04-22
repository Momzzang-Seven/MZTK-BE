package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminSettleCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;

public interface PrepareQnaInternalSettlementUseCase {

  QnaExecutionIntentResult execute(PrepareAdminSettleCommand command);
}
