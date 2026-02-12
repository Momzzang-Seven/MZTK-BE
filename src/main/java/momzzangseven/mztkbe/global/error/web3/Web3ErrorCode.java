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
      "WEB3_004", "Invalid treasury private key format", HttpStatus.BAD_REQUEST);

  private final String code;
  private final String message;
  private final HttpStatus httpStatus;
}
