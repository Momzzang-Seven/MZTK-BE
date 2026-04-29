package momzzangseven.mztkbe.global.error.treasury;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Raised by the KMS lifecycle adapter when {@code CreateAlias} fails because the alias name is
 * already bound to another KMS key. The provisioning service catches this to drive idempotent
 * recovery: when the existing alias points to a {@code PENDING_DELETION} / {@code DISABLED} key
 * (left over from a prior failed run), the service re-targets the alias via {@code UpdateAlias};
 * when the existing alias points to an active key (out-of-band creation), it bubbles up.
 */
public class KmsAliasAlreadyExistsException extends BusinessException {

  public KmsAliasAlreadyExistsException(String message) {
    super(ErrorCode.KMS_ALIAS_ALREADY_EXISTS, message);
  }

  public KmsAliasAlreadyExistsException(String message, Throwable cause) {
    super(ErrorCode.KMS_ALIAS_ALREADY_EXISTS, message, cause);
  }
}
