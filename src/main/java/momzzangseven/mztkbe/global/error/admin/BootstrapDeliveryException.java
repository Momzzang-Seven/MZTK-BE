package momzzangseven.mztkbe.global.error.admin;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Thrown when bootstrap credential delivery fails. */
public class BootstrapDeliveryException extends BusinessException {

  public BootstrapDeliveryException(String message, Throwable cause) {
    super(ErrorCode.RECOVERY_DELIVERY_FAILED, message, cause);
  }
}
