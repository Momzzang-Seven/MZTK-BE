package momzzangseven.mztkbe.modules.web3.execution.domain.model;

/**
 * Termination reasons that the execution module emits on its outbound {@code
 * ExecutionIntentTerminatedEvent}. The event carries the reason as a {@code String} on the wire
 * (see {@code ExecutionIntentTerminatedEvent#failureReason}), so listeners (QnA escrow refund
 * cascade, log-based alerts) only ever see the {@link #name()} value.
 *
 * <p>Each {@code name()} here intentionally matches a corresponding {@code Web3TxFailureReason}
 * value in the {@code transaction} module so existing string-based listeners stay compatible
 * without any cross-module enum dependency. The execution module imports this enum instead of
 * {@code transaction.domain.model.Web3TxFailureReason} to keep module boundaries clean.
 */
public enum ExecutionFailureReason {
  /** Terminal AWS KMS Sign failure (AccessDenied / NotFound / Disabled / KeyUnavailable). */
  KMS_SIGN_FAILED_TERMINAL,
  /** ECDSA signature recovery yielded an address other than the expected sponsor address. */
  SIGNATURE_INVALID
}
