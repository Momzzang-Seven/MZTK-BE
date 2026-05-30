package momzzangseven.mztkbe.modules.account.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.port.in.ReconcileAccountStatusRegistryUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadNonActiveUserStatusesPort;
import momzzangseven.mztkbe.modules.account.application.port.out.UpdateAccountStatusRegistryPort;
import org.springframework.stereotype.Service;

/**
 * Rebuilds the entire in-memory denylist from the DB snapshot of non-ACTIVE users (warmup +
 * periodic reconcile).
 *
 * <p>The DB read ({@link LoadNonActiveUserStatusesPort#loadAllNonActive()}) is passed to {@link
 * UpdateAccountStatusRegistryPort#replaceAll} as a supplier so it executes <em>inside</em> the
 * registry write lock, making the load+swap atomic against incremental put/evict events (no
 * reconcile clobber). {@code loadAllNonActive()} is already {@code @Transactional(readOnly = true)}
 * on the adapter, so this method is not itself {@code @Transactional} (no double-wrapping).
 *
 * <p>On failure it logs and leaves the denylist unchanged so a transient error never crashes the
 * scheduler — the next reconcile retries. (The warm-up runner, by contrast, treats a persistent
 * not-ready state as a boot failure.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconcileAccountStatusRegistryService
    implements ReconcileAccountStatusRegistryUseCase {

  private final LoadNonActiveUserStatusesPort loadPort;
  private final UpdateAccountStatusRegistryPort updatePort;

  @Override
  public void reconcile() {
    try {
      // Pass the loader (not an eagerly-loaded snapshot) so the DB read runs under the registry
      // lock, serialized with put/evict.
      updatePort.replaceAll(loadPort::loadAllNonActive);
      log.info("Account status denylist reconciled from DB snapshot");
    } catch (Exception e) {
      log.error(
          "Account status denylist reconcile failed — denylist left unchanged "
              + "(fail-open risk until next reconcile)",
          e);
    }
  }
}
