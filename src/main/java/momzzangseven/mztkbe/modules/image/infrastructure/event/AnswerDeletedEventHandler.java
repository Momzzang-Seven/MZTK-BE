package momzzangseven.mztkbe.modules.image.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import momzzangseven.mztkbe.modules.image.application.dto.UnlinkImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.UnlinkImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Infrastructure-layer event handler that listens for {@link AnswerDeletedEvent} and delegates
 * image unlinking to {@link UnlinkImagesByReferenceUseCase}.
 *
 * <p>Why AFTER_COMMIT: if the answer transaction is rolled back, this handler must not run.
 * AFTER_COMMIT guarantees the answer row is durably removed before image cleanup begins.
 *
 * <p>Why REQUIRES_NEW: AFTER_COMMIT fires after the original transaction has already closed, so a
 * new transaction is needed to execute the unlink UPDATE.
 *
 * <p>Failure handling: if unlinking fails, images remain linked with referenceId=answerId. The
 * cleanup scheduler filters by referenceId IS NULL, so these rows will not be auto-collected.
 * Monitor the ERROR log and investigate manually.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerDeletedEventHandler {

  private final UnlinkImagesByReferenceUseCase unlinkImagesByReferenceUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(AnswerDeletedEvent event) {
    UnlinkImagesByReferenceCommand command =
        new UnlinkImagesByReferenceCommand(ImageReferenceType.COMMUNITY_ANSWER, event.answerId());
    try {
      unlinkImagesByReferenceUseCase.execute(command);
      log.debug(
          "Successfully unlinked images for deleted answer: referenceId={}", event.answerId());
    } catch (Exception e) {
      log.error(
          "Failed to unlink images for deleted answer {}: {}", event.answerId(), e.getMessage(), e);
    }
  }
}
