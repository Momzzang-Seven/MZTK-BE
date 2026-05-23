package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SignRequestUnavailableReason;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;

final class ExecutionSignRequestAvailability {

  private ExecutionSignRequestAvailability() {}

  static boolean canExposeSignRequest(
      ExecutionIntent intent, LocalDateTime now, long eip7702MinimumRemainingSeconds) {
    return intent.shouldExposeSignRequest()
        && unavailableReason(intent, now, eip7702MinimumRemainingSeconds) == null;
  }

  static SignRequestUnavailableReason unavailableReason(
      ExecutionIntent intent, LocalDateTime now, long eip7702MinimumRemainingSeconds) {
    if (!intent.shouldExposeSignRequest()) {
      return null;
    }
    if (!intent.getExpiresAt().isAfter(now)) {
      return SignRequestUnavailableReason.AUTH_EXPIRED;
    }
    if (intent.getMode() == ExecutionMode.EIP7702
        && !hasMinimumRemainingTime(intent, now, eip7702MinimumRemainingSeconds)) {
      return SignRequestUnavailableReason.EIP7702_DEADLINE_TOO_CLOSE;
    }
    return null;
  }

  static boolean hasMinimumRemainingTime(
      ExecutionIntent intent, LocalDateTime now, long minimumRemainingSeconds) {
    return !intent.getExpiresAt().isBefore(now.plusSeconds(minimumRemainingSeconds));
  }
}
