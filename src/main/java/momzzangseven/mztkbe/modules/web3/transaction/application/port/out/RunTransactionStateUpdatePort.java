package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.util.function.Supplier;

/** Runs short web3 transaction state mutations in an explicit transaction boundary. */
public interface RunTransactionStateUpdatePort {

  /** Executes the given state update in a new transaction. */
  <T> T requiresNew(Supplier<T> action);
}
