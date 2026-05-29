package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;

/**
 * Grants XP synchronously and, if the grant fails, guarantees eventual delivery by enqueueing the
 * command to a durable outbox for a reconciliation scheduler to retry.
 *
 * <p>This is <b>not</b> a transaction boundary: callers (write-path facades) invoke it only after
 * their entity transaction has committed and released its connection, so the grant runs on a fresh
 * connection and the request never holds two connections at once.
 */
public interface GuaranteedGrantXpUseCase {
  GrantXpResult execute(GrantXpCommand command);
}
