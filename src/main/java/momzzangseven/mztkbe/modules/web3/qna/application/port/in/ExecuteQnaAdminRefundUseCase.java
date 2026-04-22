package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.ExecuteQnaAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;

public interface ExecuteQnaAdminRefundUseCase {

  QnaExecutionIntentResult execute(ExecuteQnaAdminRefundCommand command);
}
