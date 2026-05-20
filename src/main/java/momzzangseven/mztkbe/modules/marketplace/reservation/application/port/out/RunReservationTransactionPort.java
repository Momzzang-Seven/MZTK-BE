package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.util.function.Supplier;

/** Runs reservation application work in a transaction boundary owned by infrastructure. */
public interface RunReservationTransactionPort {

  <T> T requiresNew(Supplier<T> supplier);

  <T> T notSupported(Supplier<T> supplier);

  default void requiresNew(Runnable action) {
    requiresNew(
        () -> {
          action.run();
          return null;
        });
  }
}
