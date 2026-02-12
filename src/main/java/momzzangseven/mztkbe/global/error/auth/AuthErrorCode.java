package momzzangseven.mztkbe.global.error.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.AppErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements AppErrorCode {
  USER_NOT_FOUND("AUTH_001", "User not found", HttpStatus.UNAUTHORIZED),
  INVALID_CREDENTIALS("AUTH_002", "Invalid email or password", HttpStatus.UNAUTHORIZED),
  UNSUPPORTED_PROVIDER("AUTH_003", "Unsupported authentication provider", HttpStatus.BAD_REQUEST),
  INVALID_TOKEN("AUTH_004", "Invalid or expired token", HttpStatus.UNAUTHORIZED),
  UNAUTHORIZED_ACCESS("AUTH_005", "Unauthorized access", HttpStatus.FORBIDDEN),
  USER_NOT_AUTHENTICATED("AUTH_006", "User is not authenticated", HttpStatus.UNAUTHORIZED),
  GOOGLE_OFFLINE_CONSENT_REQUIRED(
      "AUTH_007",
      "Google offline consent required (prompt=consent&access_type=offline)",
      HttpStatus.CONFLICT),
  STEP_UP_REQUIRED("AUTH_008", "Step-up authentication required", HttpStatus.FORBIDDEN);

  private final String code;
  private final String message;
  private final HttpStatus httpStatus;
}
