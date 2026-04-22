package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;

public interface PrepareQnaAdminRefundUseCase {

  QnaExecutionIntentResult execute(PrepareAdminRefundCommand command);
}
