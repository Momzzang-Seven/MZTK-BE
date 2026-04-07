package momzzangseven.mztkbe.modules.post.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import momzzangseven.mztkbe.modules.post.application.port.in.DeleteAnswerLikesUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostLikeAnswerDeletedEventHandler {

  private final DeleteAnswerLikesUseCase deleteAnswerLikesUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(AnswerDeletedEvent event) {
    try {
      deleteAnswerLikesUseCase.deleteAnswerLikes(event.answerId());
      log.debug(
          "Successfully deleted answer likes for deleted answer: answerId={}", event.answerId());
    } catch (Exception e) {
      log.error(
          "Failed to delete answer likes for deleted answer {}: {}",
          event.answerId(),
          e.getMessage(),
          e);
    }
  }
}
