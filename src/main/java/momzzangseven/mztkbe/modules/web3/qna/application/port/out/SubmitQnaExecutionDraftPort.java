package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;

public interface SubmitQnaExecutionDraftPort {

  QnaExecutionIntentResult submit(QnaExecutionDraft draft);
}
