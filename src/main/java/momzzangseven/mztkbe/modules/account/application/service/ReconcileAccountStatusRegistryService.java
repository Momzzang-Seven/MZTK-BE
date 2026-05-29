package momzzangseven.mztkbe.modules.account.application.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.port.in.ReconcileAccountStatusRegistryUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadNonActiveUserStatusesPort;
import momzzangseven.mztkbe.modules.account.application.port.out.UpdateAccountStatusRegistryPort;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.springframework.stereotype.Service;

/**
 * Rebuilds the entire in-memory denylist from the DB snapshot of non-ACTIVE users (warmup +
 * periodic reconcile).
 *
 * <p>The DB read happens inside {@link LoadNonActiveUserStatusesPort#loadAllNonActive()}, which is
 * already {@code @Transactional(readOnly = true)} on the adapter, so this method is not itself
 * {@code @Transactional} (no double-wrapping). On failure it logs and leaves the denylist unchanged
 * so a transient error never crashes startup or the scheduler — the next reconcile retries.
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
      Map<Long, AccountStatus> snapshot = loadPort.loadAllNonActive();
      updatePort.replaceAll(snapshot);
      log.info("Account status denylist reconciled: nonActive={}", snapshot.size());
    } catch (Exception e) {
      log.error(
          "Account status denylist reconcile failed — denylist left unchanged "
              + "(fail-open risk until next reconcile)",
          e);
    }
  }
}
