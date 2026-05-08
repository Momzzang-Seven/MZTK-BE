package momzzangseven.mztkbe.modules.web3.qna.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAnswerPublicationEvidence;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaAnswerPublicationEvidenceUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetQnaAnswerPublicationEvidenceService
    implements GetQnaAnswerPublicationEvidenceUseCase {

  private final QnaProjectionPersistencePort qnaProjectionPersistencePort;
  private final LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;

  @Override
  public QnaAnswerPublicationEvidence getAnswerPublicationEvidence(
      Long answerId, String executionIntentId) {
    boolean projectionExists =
        qnaProjectionPersistencePort.findAnswerByAnswerId(answerId).isPresent();
    return loadQnaExecutionIntentStatePort
        .loadByExecutionIntentId(executionIntentId)
        .map(
            state ->
                new QnaAnswerPublicationEvidence(
                    answerId,
                    executionIntentId,
                    state.actionType(),
                    state.status(),
                    state.failureReason(),
                    projectionExists))
        .orElse(
            new QnaAnswerPublicationEvidence(
                answerId, executionIntentId, null, null, null, projectionExists));
  }

  @Override
  public int repairQuestionAnswerCounts() {
    return qnaProjectionPersistencePort.repairQuestionAnswerCounts();
  }
}
