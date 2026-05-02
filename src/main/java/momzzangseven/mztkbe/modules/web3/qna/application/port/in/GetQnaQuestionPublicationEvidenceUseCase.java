package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaQuestionPublicationEvidenceQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaQuestionPublicationEvidenceResult;

public interface GetQnaQuestionPublicationEvidenceUseCase {

  QnaQuestionPublicationEvidenceResult execute(GetQnaQuestionPublicationEvidenceQuery query);
}
