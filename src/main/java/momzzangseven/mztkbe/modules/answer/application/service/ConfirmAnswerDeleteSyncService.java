package momzzangseven.mztkbe.modules.answer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.ConfirmAnswerDeleteSyncUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.PublishAnswerDeletedEventPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the local hard-delete side effects that run after a confirmed on-chain answer delete.
 *
 * <p>The service is intentionally idempotent so repeated confirmation events do not fail when the
 * local answer row has already been removed.
 */
@Service
@RequiredArgsConstructor
public class ConfirmAnswerDeleteSyncService implements ConfirmAnswerDeleteSyncUseCase {

  private final LoadAnswerPort loadAnswerPort;
  private final SaveAnswerPort saveAnswerPort;
  private final DeleteAnswerPort deleteAnswerPort;
  private final PublishAnswerDeletedEventPort publishAnswerDeletedEventPort;

  @Override
  @Transactional
  public void confirmDeleted(Long answerId, String executionIntentId) {
    loadAnswerPort
        .loadAnswerForUpdate(answerId)
        .ifPresent(
            answer -> {
              if (answer.matchesCurrentDeleteExecutionIntent(executionIntentId)) {
                deleteAnswerPort.deleteAnswer(answerId);
                publishAnswerDeletedEventPort.publish(new AnswerDeletedEvent(answerId));
                return;
              }
              saveAnswerPort.saveAnswer(
                  answer.markReconciliationRequired(
                      "answer delete confirmation did not match current local delete intent",
                      executionIntentId));
            });
  }

  @Override
  @Transactional
  public void rollbackDelete(
      Long answerId, String executionIntentId, String terminalStatus, String failureReason) {
    saveAnswerPort.rollbackDeleteIfCurrent(
        answerId, executionIntentId, terminalStatus, failureReason);
  }
}
