package momzzangseven.mztkbe.modules.web3.qna.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.GetQnaQuestionPublicationEvidenceQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaQuestionPublicationEvidenceResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.GetQnaQuestionPublicationEvidenceUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionIntentStateView;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;

@RequiredArgsConstructor
public class GetQnaQuestionPublicationEvidenceService
    implements GetQnaQuestionPublicationEvidenceUseCase {

  private final QnaProjectionPersistencePort qnaProjectionPersistencePort;
  private final LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;

  @Override
  public QnaQuestionPublicationEvidenceResult execute(
      GetQnaQuestionPublicationEvidenceQuery query) {
    QnaQuestionProjection projection =
        qnaProjectionPersistencePort.findQuestionByPostId(query.postId()).orElse(null);
    String rootIdempotencyKey =
        QnaEscrowIdempotencyKeyFactory.create(
            QnaExecutionActionType.QNA_QUESTION_CREATE,
            query.requesterUserId(),
            query.postId(),
            null);
    QnaExecutionIntentStateView latestCreate =
        loadQnaExecutionIntentStatePort
            .loadLatestByRootIdempotencyKey(rootIdempotencyKey)
            .orElse(null);

    return new QnaQuestionPublicationEvidenceResult(
        projection != null,
        projection == null ? null : projection.getState().name(),
        latestCreate != null && latestCreate.isActive(),
        latestCreate != null && latestCreate.isTerminal(),
        latestCreate == null ? null : latestCreate.status().name(),
        latestCreate == null ? null : latestCreate.executionIntentId());
  }
}
