package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.Set;

final class ReservationExecutionTerminalStatusPolicy {

  private static final Set<String> RETRYABLE_TERMINAL_STATUSES =
      Set.of("FAILED_ONCHAIN", "EXPIRED", "CANCELED", "NONCE_STALE");

  private ReservationExecutionTerminalStatusPolicy() {}

  static boolean isRetryableTerminal(String status) {
    return RETRYABLE_TERMINAL_STATUSES.contains(status);
  }
}
