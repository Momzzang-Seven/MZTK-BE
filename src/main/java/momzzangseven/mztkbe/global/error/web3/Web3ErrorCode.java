package momzzangseven.mztkbe.global.error.web3;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.AppErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum Web3ErrorCode implements AppErrorCode {
  WEB3_INVALID_INPUT("WEB3_001", "Invalid web3 input", HttpStatus.BAD_REQUEST),
  WEB3_TRANSACTION_NOT_FOUND("WEB3_002", "Web3 transaction not found", HttpStatus.NOT_FOUND),
  WEB3_TRANSACTION_STATE_INVALID("WEB3_003", "Invalid web3 transaction state", HttpStatus.CONFLICT),
  WEB3_TREASURY_PRIVATE_KEY_INVALID(
      "WEB3_004", "Invalid treasury private key format", HttpStatus.BAD_REQUEST),
  AUTH_EXPIRED("AUTH_EXPIRED", "Authorization expired", HttpStatus.BAD_REQUEST),
  AUTH_NONCE_MISMATCH("AUTH_NONCE_MISMATCH", "Authority nonce mismatch", HttpStatus.CONFLICT),
  DELEGATE_NOT_ALLOWLISTED(
      "DELEGATE_NOT_ALLOWLISTED", "Delegate target is not allowlisted", HttpStatus.BAD_REQUEST),
  SPONSOR_GAS_LIMIT_EXCEEDED(
      "SPONSOR_GAS_LIMIT_EXCEEDED", "Sponsor gas limit exceeded", HttpStatus.BAD_REQUEST),
  SPONSOR_DAILY_LIMIT_EXCEEDED(
      "SPONSOR_DAILY_LIMIT_EXCEEDED", "Sponsor daily limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
  SPONSOR_AMOUNT_LIMIT_EXCEEDED(
      "SPONSOR_AMOUNT_LIMIT_EXCEEDED", "Sponsor amount limit exceeded", HttpStatus.BAD_REQUEST),
  IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "Idempotency conflict", HttpStatus.CONFLICT);

  private final String code;
  private final String message;
  private final HttpStatus httpStatus;
}
