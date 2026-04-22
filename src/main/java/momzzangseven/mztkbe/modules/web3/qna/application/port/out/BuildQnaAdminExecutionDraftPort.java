package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;

public interface BuildQnaAdminExecutionDraftPort {

  QnaExecutionDraft build(QnaEscrowExecutionRequest request);
}
