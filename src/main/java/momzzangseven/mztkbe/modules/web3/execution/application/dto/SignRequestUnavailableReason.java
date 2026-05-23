package momzzangseven.mztkbe.modules.web3.execution.application.dto;

/** Explains why a signable execution intent cannot expose a sign request anymore. */
public enum SignRequestUnavailableReason {
  AUTH_EXPIRED,
  EIP7702_DEADLINE_TOO_CLOSE
}
