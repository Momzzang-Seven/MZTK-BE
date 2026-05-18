package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.function.Supplier;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;

/** Test-only transaction port that executes callbacks synchronously. */
public final class ReservationTestTransactionPort {

  private static final RunReservationTransactionPort DIRECT =
      new RunReservationTransactionPort() {
        @Override
        public <T> T requiresNew(Supplier<T> supplier) {
          return supplier.get();
        }

        @Override
        public <T> T notSupported(Supplier<T> supplier) {
          return supplier.get();
        }
      };

  private ReservationTestTransactionPort() {}

  public static RunReservationTransactionPort direct() {
    return DIRECT;
  }
}
