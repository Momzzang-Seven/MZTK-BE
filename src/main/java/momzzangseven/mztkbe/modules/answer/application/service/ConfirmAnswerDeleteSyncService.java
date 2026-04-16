package momzzangseven.mztkbe.modules.answer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.ConfirmAnswerDeleteSyncUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.DeleteAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
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
  private final DeleteAnswerPort deleteAnswerPort;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public void confirmDeleted(Long answerId) {
    loadAnswerPort
        .loadAnswerForUpdate(answerId)
        .ifPresent(
            answer -> {
              deleteAnswerPort.deleteAnswer(answerId);
              eventPublisher.publishEvent(new AnswerDeletedEvent(answerId));
            });
  }
}
