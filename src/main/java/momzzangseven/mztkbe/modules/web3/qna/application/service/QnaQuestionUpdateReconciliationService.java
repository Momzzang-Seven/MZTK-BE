package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.RunQnaQuestionUpdateReconciliationCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.RunQnaQuestionUpdateReconciliationResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.RunQnaQuestionUpdateReconciliationUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateConfirmationSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateStatePersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionUpdateState;

@RequiredArgsConstructor
@Slf4j
public class QnaQuestionUpdateReconciliationService
    implements RunQnaQuestionUpdateReconciliationUseCase {

  private final QnaQuestionUpdateStatePersistencePort statePersistencePort;
  private final QnaQuestionUpdateConfirmationSyncPort confirmationSyncPort;

  @Override
  public RunQnaQuestionUpdateReconciliationResult run(
      RunQnaQuestionUpdateReconciliationCommand command) {
    RunQnaQuestionUpdateReconciliationCommand effectiveCommand =
        command == null ? new RunQnaQuestionUpdateReconciliationCommand(null) : command;
    int limit = effectiveCommand.normalizedLimit();
    List<QnaQuestionUpdateState> candidates =
        statePersistencePort.findConfirmedIntentBoundForReconciliation(limit);

    int repaired = 0;
    int skipped = 0;
    int failed = 0;
    for (QnaQuestionUpdateState candidate : candidates) {
      try {
        if (confirmationSyncPort.syncConfirmedQuestionUpdate(
            candidate.getExecutionIntentPublicId())) {
          repaired++;
        } else {
          skipped++;
        }
      } catch (RuntimeException e) {
        failed++;
        statePersistencePort.recordSyncFailure(
            candidate.getExecutionIntentPublicId(), e.getClass().getSimpleName(), e.getMessage());
        log.warn(
            "Failed to reconcile qna question update state: postId={}, version={}, intentId={}",
            candidate.getPostId(),
            candidate.getUpdateVersion(),
            candidate.getExecutionIntentPublicId(),
            e);
      }
    }
    return new RunQnaQuestionUpdateReconciliationResult(
        candidates.size(), repaired, skipped, failed);
  }
}
