package momzzangseven.mztkbe.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Centralized error code management.
 *
 * <p>Each error code has: - code: Unique identifier for the error (e.g., "AUTH_001") - message:
 * Default error message - httpStatus: HTTP status code to return
 *
 * <p>Naming Convention: - AUTH_xxx: Authentication/Authorization errors - SIGNUP_xxx: User
 * registration errors - WALLET_xxx: Wallet-related errors - CHALLENGE_xxx: Challenge verification
 * errors - VALIDATION_xxx: Input validation errors - INTERNAL_xxx: System errors
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  // ========================================
  // Authentication Errors (AUTH_xxx)
  // ========================================
  USER_NOT_FOUND(
      "AUTH_001", "User not found", HttpStatus.UNAUTHORIZED // 401
      ),

  INVALID_CREDENTIALS(
      "AUTH_002", "Invalid email or password", HttpStatus.UNAUTHORIZED // 401
      ),

  UNSUPPORTED_PROVIDER(
      "AUTH_003", "Unsupported authentication provider", HttpStatus.BAD_REQUEST // 400
      ),

  INVALID_TOKEN(
      "AUTH_004", "Invalid or expired token", HttpStatus.UNAUTHORIZED // 401
      ),

  UNAUTHORIZED_ACCESS(
      "AUTH_005", "Unauthorized access", HttpStatus.FORBIDDEN // 403
      ),

  // ========================================
  // Signup Errors (SIGNUP_xxx)
  // ========================================
  DUPLICATE_EMAIL(
      "SIGNUP_001", "This email address is already in use", HttpStatus.CONFLICT // 409
      ),

  INVALID_PASSWORD(
      "SIGNUP_002",
      "Password must be at least 8 characters and include letters, numbers, and special characters",
      HttpStatus.BAD_REQUEST // 400
      ),

  INVALID_EMAIL_FORMAT(
      "SIGNUP_003", "Invalid email format", HttpStatus.BAD_REQUEST // 400
      ),

  // ========================================
  // Token Errors (TOKEN_xxx)
  // ========================================
  REFRESH_TOKEN_NOT_FOUND(
      "TOKEN_001", "Refresh token not found", HttpStatus.BAD_REQUEST // 400
      ),

  REFRESH_TOKEN_INVALID(
      "TOKEN_002", "Invalid refresh token", HttpStatus.UNAUTHORIZED // 401
      ),

  TOKEN_SECURITY_THREAT(
      "TOKEN_003", "Security threat detected. Token has been revoked", HttpStatus.FORBIDDEN // 403
      ),

  // ========================================
  // Wallet Errors (WALLET_xxx)
  // ========================================
  INVALID_WALLET_ADDRESS(
      "WALLET_001", "Invalid wallet address format", HttpStatus.BAD_REQUEST // 400
      ),

  WALLET_ALREADY_LINKED(
      "WALLET_002",
      "This wallet address is already linked to another account",
      HttpStatus.CONFLICT // 409
      ),

  WALLET_NOT_CONNECTED(
      "WALLET_003", "No wallet connected to this account", HttpStatus.BAD_REQUEST // 400
      ),

  // ========================================
  // Challenge Errors (CHALLENGE_xxx)
  // ========================================
  CHALLENGE_NOT_FOUND(
      "CHALLENGE_001", "Challenge not found or expired", HttpStatus.BAD_REQUEST // 400
      ),

  CHALLENGE_ALREADY_USED(
      "CHALLENGE_002", "This challenge has already been used", HttpStatus.BAD_REQUEST // 400
      ),

  SIGNATURE_VERIFICATION_FAILED(
      "CHALLENGE_003", "Signature verification failed", HttpStatus.UNAUTHORIZED // 401
      ),

  // ========================================
  // Validation Errors (VALIDATION_xxx)
  // ========================================
  INVALID_INPUT(
      "VALIDATION_001", "Invalid input data", HttpStatus.BAD_REQUEST // 400
      ),

  MISSING_REQUIRED_FIELD(
      "VALIDATION_002", "Required field is missing", HttpStatus.BAD_REQUEST // 400
      ),

  // ========================================
  // System Errors (INTERNAL_xxx)
  // ========================================
  INTERNAL_SERVER_ERROR(
      "INTERNAL_001", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR // 500
      ),

  EXTERNAL_API_ERROR(
      "INTERNAL_002", "External API call failed", HttpStatus.BAD_GATEWAY // 502
      ),

  DATABASE_ERROR(
      "INTERNAL_003", "Database operation failed", HttpStatus.INTERNAL_SERVER_ERROR // 500
      );

  private final String code;
  private final String message;
  private final HttpStatus httpStatus;

  /**
   * Format error message with additional context.
   *
   * @param args Arguments to format into the message
   * @return Formatted error message
   */
  public String formatMessage(Object... args) {
    return String.format(message, args);
  }
}
