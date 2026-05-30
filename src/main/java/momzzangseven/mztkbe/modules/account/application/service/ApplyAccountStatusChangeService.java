package momzzangseven.mztkbe.modules.account.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.dto.ApplyAccountStatusChangeCommand;
import momzzangseven.mztkbe.modules.account.application.port.in.ApplyAccountStatusChangeUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.UpdateAccountStatusRegistryPort;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.springframework.stereotype.Service;

/**
 * Idempotently applies a single account-status change to the in-memory denylist.
 *
 * <p>Deliberately <strong>not</strong> {@code @Transactional}: this is a pure in-memory update and
 * must acquire zero DB connections — that connection-free property is the whole point of MOM-464.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyAccountStatusChangeService implements ApplyAccountStatusChangeUseCase {

  private final UpdateAccountStatusRegistryPort updatePort;

  @Override
  public void execute(ApplyAccountStatusChangeCommand command) {
    AccountStatus status = command.status();
    if (status == null || status == AccountStatus.ACTIVE) {
      updatePort.evict(command.userId());
      log.debug("Denylist apply: evict userId={} (status={})", command.userId(), status);
    } else {
      updatePort.put(command.userId(), status);
      log.debug("Denylist apply: put userId={} status={}", command.userId(), status);
    }
  }
}
