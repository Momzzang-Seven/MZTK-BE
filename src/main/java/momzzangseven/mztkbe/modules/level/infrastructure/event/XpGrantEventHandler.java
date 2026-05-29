package momzzangseven.mztkbe.modules.level.infrastructure.event;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.comment.domain.event.CommentCreatedEvent;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.location.domain.event.LocationVerifiedEvent;
import momzzangseven.mztkbe.modules.post.domain.event.PostCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Grants XP in response to producer domain events, owning the XP-reward concern that previously
 * lived in each producer module's outbound adapter.
 *
 * <p><b>Why AFTER_COMMIT:</b> the producer's business transaction must be durably committed before
 * XP is granted. AFTER_COMMIT fires only after the producer transaction commits and releases its
 * connection, so this handler never holds a second connection concurrently with the producer
 * transaction. This is the core fix for the nested {@code REQUIRES_NEW} connection-doubling that
 * halved the effective pool. It also eliminates the orphan-XP mode where XP was granted for an
 * entity whose own transaction later rolled back.
 *
 * <p><b>Why REQUIRES_NEW:</b> at AFTER_COMMIT the original transaction is already closed, so a
 * fresh physical transaction is required for the XP ledger write. {@code GrantXpService} (REQUIRED)
 * joins this new transaction, keeping the whole XP grant on a single connection.
 *
 * <p><b>Failure handling:</b> all exceptions are caught and logged, never rethrown — a failed grant
 * must not affect the already-committed producer entity. Because {@code GrantXpService} is
 * idempotent (idempotency key + daily cap), a missed event can be safely reprocessed.
 *
 * <p><b>Idempotency keys:</b> the key/sourceRef templates are byte-identical to the values the old
 * producer-side adapters wrote, so previously persisted ledger rows still de-duplicate correctly
 * after this refactor. The templates are an xp-ledger concept owned by the {@code level} module, so
 * keeping them here is more cohesive than the prior producer-side placement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XpGrantEventHandler {

  private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

  private final GrantXpUseCase grantXpUseCase;

  /** Grants COMMENT XP after a comment-creation transaction commits. */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onCommentCreated(CommentCreatedEvent event) {
    grant(
        event.userId(),
        XpType.COMMENT,
        event.occurredAt(),
        "comment:create:" + event.commentId(),
        "comment-creation:" + event.commentId());
  }

  /** Grants POST XP after a post-creation transaction commits (free and question boards). */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onPostCreated(PostCreatedEvent event) {
    grant(
        event.userId(),
        XpType.POST,
        event.occurredAt(),
        "post:create:" + event.postId(),
        "post-creation:" + event.postId());
  }

  /** Grants WORKOUT XP after a successful location-verification transaction commits. */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onLocationVerified(LocationVerifiedEvent event) {
    String idempotencyKey =
        "workout:location-verify:"
            + event.userId()
            + ":"
            + event.locationId()
            + ":"
            + event.verifiedAt().format(YYYYMMDD);
    grant(
        event.userId(),
        XpType.WORKOUT,
        event.verifiedAt(),
        idempotencyKey,
        "location-verification:" + event.locationId());
  }

  private void grant(
      Long userId, XpType type, LocalDateTime occurredAt, String idempotencyKey, String sourceRef) {
    try {
      GrantXpResult result =
          grantXpUseCase.execute(
              GrantXpCommand.of(userId, type, occurredAt, idempotencyKey, sourceRef));
      log.info(
          "XP grant processed: type={}, userId={}, key={}, status={}, grantedXp={}",
          type,
          userId,
          idempotencyKey,
          result.status(),
          result.grantedXp());
    } catch (Exception e) {
      log.error("XP grant failed: type={}, userId={}, key={}", type, userId, idempotencyKey, e);
    }
  }
}
