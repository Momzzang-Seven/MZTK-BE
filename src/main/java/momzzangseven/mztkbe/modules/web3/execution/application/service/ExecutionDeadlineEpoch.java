package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;

final class ExecutionDeadlineEpoch {

  private ExecutionDeadlineEpoch() {}

  static BigInteger toEpochSeconds(LocalDateTime deadline, Clock appClock) {
    return BigInteger.valueOf(deadline.atZone(appClock.getZone()).toEpochSecond());
  }

  static long toEpochSecondsLong(LocalDateTime deadline, Clock appClock) {
    return toEpochSeconds(deadline, appClock).longValueExact();
  }
}
