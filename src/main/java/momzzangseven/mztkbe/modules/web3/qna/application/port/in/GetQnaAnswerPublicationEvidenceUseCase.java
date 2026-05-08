package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAnswerPublicationEvidence;

public interface GetQnaAnswerPublicationEvidenceUseCase {

  QnaAnswerPublicationEvidence getAnswerPublicationEvidence(
      Long answerId, String executionIntentId);

  int repairQuestionAnswerCounts();
}
