package momzzangseven.mztkbe.modules.post.infrastructure.event;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.domain.event.AnswerDeletedEvent;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PostLikeAnswerDeletedEventHandler {

  private final PostLikePersistencePort postLikePersistencePort;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(AnswerDeletedEvent event) {
    postLikePersistencePort.deleteByTarget(PostLikeTargetType.ANSWER, event.answerId());
  }
}
