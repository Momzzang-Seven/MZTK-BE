package momzzangseven.mztkbe.modules.account.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.dto.ApplyAccountStatusChangeCommand;
import momzzangseven.mztkbe.modules.account.application.port.in.ApplyAccountStatusChangeUseCase;
import momzzangseven.mztkbe.modules.account.domain.event.UserAccountInvalidatedEvent;
import momzzangseven.mztkbe.modules.account.domain.event.UserAccountStatusChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Maintains the in-memory account-status denylist by consuming two account domain events.
 *
 * <p>Both listeners run {@code AFTER_COMMIT} so that a rolled-back account write never mutates the
 * denylist, and both run with {@link Propagation#NOT_SUPPORTED} so the pure in-memory put/evict
 * acquires ZERO HikariCP connection — the entire point of MOM-464 (moving account-status checks off
 * the per-request DB path).
 *
 * <p>The two events are intentionally kept separate (status-change vs. hard-delete) per design §5
 * to avoid put/evict ordering races on the same {@code userId}: {@link
 * UserAccountStatusChangedEvent} drives put/evict from a real status transition, while {@link
 * UserAccountInvalidatedEvent} is a pure evict from a hard delete.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountStatusRegistryEventHandler {

  private final ApplyAccountStatusChangeUseCase applyAccountStatusChangeUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void onStatusChanged(UserAccountStatusChangedEvent event) {
    try {
      applyAccountStatusChangeUseCase.execute(
          new ApplyAccountStatusChangeCommand(event.userId(), event.status()));
      log.debug(
          "Denylist updated from status change: userId={}, status={}",
          event.userId(),
          event.status());
    } catch (Exception e) {
      log.error("Failed to apply account status change to denylist: userId={}", event.userId(), e);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void onInvalidated(UserAccountInvalidatedEvent event) {
    try {
      applyAccountStatusChangeUseCase.execute(
          new ApplyAccountStatusChangeCommand(event.userId(), null));
      log.debug("Denylist evicted from invalidation: userId={}", event.userId());
    } catch (Exception e) {
      log.error("Failed to evict account from denylist: userId={}", event.userId(), e);
    }
  }
}
