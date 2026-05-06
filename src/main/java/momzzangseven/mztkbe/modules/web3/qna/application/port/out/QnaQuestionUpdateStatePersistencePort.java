package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionUpdateState;

public interface QnaQuestionUpdateStatePersistencePort {

  Optional<QnaQuestionUpdateState> findLatestByPostIdForUpdate(Long postId);

  Optional<QnaQuestionUpdateState> findLatestByPostId(Long postId);

  Optional<QnaQuestionUpdateState> findByExecutionIntentPublicIdForUpdate(String publicId);

  List<QnaQuestionUpdateState> findConfirmedIntentBoundForReconciliation(int limit);

  QnaQuestionUpdateState save(QnaQuestionUpdateState state);

  int markNonTerminalStaleByPostId(Long postId);

  Optional<QnaQuestionUpdateState> bindExecutionIntent(
      Long postId, Long updateVersion, String updateToken, String executionIntentPublicId);

  Optional<QnaQuestionUpdateState> markPreparationFailed(
      Long postId,
      Long updateVersion,
      String updateToken,
      String errorCode,
      String errorReason,
      boolean retryable);

  Optional<QnaQuestionUpdateState> markPreparationFailedByExecutionIntentPublicId(
      String executionIntentPublicId, String errorCode, String errorReason, boolean retryable);

  Optional<QnaQuestionUpdateState> recordSyncFailure(
      String executionIntentPublicId, String errorCode, String errorReason);

  Optional<QnaQuestionUpdateState> markConfirmed(String executionIntentPublicId);

  Optional<QnaQuestionUpdateState> markStaleByExecutionIntentPublicId(
      String executionIntentPublicId);
}
