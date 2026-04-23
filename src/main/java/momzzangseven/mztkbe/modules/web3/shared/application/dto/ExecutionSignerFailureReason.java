package momzzangseven.mztkbe.modules.web3.shared.application.dto;

public enum ExecutionSignerFailureReason {
  NONE,
  KEY_ENCRYPTION_KEY_MISSING,
  DECRYPT_FAILED,
  CORRUPTED_SLOT,
  ADDRESS_MISMATCH
}
