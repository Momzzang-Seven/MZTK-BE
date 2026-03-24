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

@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerDeletedEventHandler {

  private final UnlinkImagesByReferenceUseCase unlinkImagesByReferenceUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(AnswerDeletedEvent event) {
    try {
      unlinkImagesByReferenceUseCase.execute(
          new UnlinkImagesByReferenceCommand(
              ImageReferenceType.COMMUNITY_ANSWER, event.answerId()));
      log.debug("Successfully unlinked images for deleted answer: answerId={}", event.answerId());
    } catch (Exception e) {
      log.error(
          "Failed to unlink images for deleted answer {}: {}", event.answerId(), e.getMessage(), e);
    }
  }
}
