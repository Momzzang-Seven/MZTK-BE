package momzzangseven.mztkbe.modules.level.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.application.dto.PendingXpGrant;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.XpGrantOutboxPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes a single outbox row inside one transaction: locks the row ({@code FOR UPDATE SKIP
 * LOCKED}), replays the (idempotent) grant, and marks it DONE — all atomically.
 *
 * <p>Kept as a separate bean so its {@code @Transactional} boundary is honoured when called from
 * the non-transactional {@link XpGrantReconciliationService} (Spring AOP self-invocation would
 * bypass the proxy otherwise).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XpGrantOutboxProcessor {

  private final XpGrantOutboxPort outboxPort;
  private final GrantXpUseCase grantXpUseCase;

  /**
   * @return {@code true} if the row was claimed and granted; {@code false} if another worker holds
   *     it or it is no longer PENDING.
   */
  @Transactional
  public boolean process(Long outboxId) {
    Optional<PendingXpGrant> claimed = outboxPort.claimForProcessing(outboxId);
    if (claimed.isEmpty()) {
      return false;
    }
    grantXpUseCase.execute(claimed.get().command());
    outboxPort.markDone(outboxId);
    return true;
  }
}
