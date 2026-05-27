package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.util.function.Supplier;

/** Runs execution state mutations inside an infrastructure-owned transaction boundary. */
public interface RunExecutionTransactionPort {

  <T> T requiresNew(Supplier<T> action);

  default void requiresNew(Runnable action) {
    requiresNew(
        () -> {
          action.run();
          return null;
        });
  }
}
