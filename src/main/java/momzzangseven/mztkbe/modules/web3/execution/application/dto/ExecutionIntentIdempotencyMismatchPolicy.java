package momzzangseven.mztkbe.modules.web3.execution.application.dto;

public enum ExecutionIntentIdempotencyMismatchPolicy {
  CANCEL_AWAITING_SIGNATURE_AND_CREATE_NEW,
  REJECT_ON_MISMATCH
}
