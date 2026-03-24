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

  USER_NOT_AUTHENTICATED(
      "AUTH_006", "User is not authenticated", HttpStatus.UNAUTHORIZED // 401
      ),

  /**
   * Google OAuth token exchange may omit {@code refresh_token}.
   *
   * <p>In this project, we acquire/store Google refresh tokens at "revoke-needed" moments (step-up
   * for withdrawal/disconnect). If the token response does not include a refresh token AND we have
   * no previously stored refresh token, the client must re-run OAuth with {@code
   * prompt=consent&access_type=offline} and retry.
   */
  GOOGLE_OFFLINE_CONSENT_REQUIRED(
      "AUTH_007",
      "Google offline consent required (prompt=consent&access_type=offline)",
      HttpStatus.CONFLICT // 409
      ),

  STEP_UP_REQUIRED(
      "AUTH_008", "Step-up authentication required", HttpStatus.FORBIDDEN // 403
      ),

  // ========================================
  // User Errors (USER_xxx)
  // ========================================
  ILLEGAL_ADMIN_GRANT("USER_001", "Cannot self-assign ADMIN role", HttpStatus.BAD_REQUEST),

  INVALID_ROLE("USER_002", "Invalid role value", HttpStatus.BAD_REQUEST),

  ILLEGAL_TRAINER_GRANT("USER_003", "Cannot assign TRAINER role", HttpStatus.BAD_REQUEST),

  USER_WITHDRAWN("USER_004", "User account is withdrawn", HttpStatus.CONFLICT),

  // ========================================
  // Level Errors (LEVEL_xxx)
  // ========================================
  NOT_ENOUGH_XP("LEVEL_001", "Not enough XP to level up", HttpStatus.CONFLICT),
  MAX_LEVEL_REACHED("LEVEL_002", "Max level reached", HttpStatus.CONFLICT),
  LEVEL_UP_ALREADY_PROCESSED("LEVEL_003", "Level up already processed", HttpStatus.CONFLICT),
  REWARD_FAILED_ONCHAIN(
      "LEVEL_004", "Reward transaction failed onchain and cannot be retried", HttpStatus.CONFLICT),
  REWARD_INTENT_CREATION_FAILED(
      "LEVEL_005", "Failed to create reward transaction intent", HttpStatus.INTERNAL_SERVER_ERROR),
  REWARD_TREASURY_ADDRESS_INVALID(
      "LEVEL_006", "Treasury address configuration is invalid", HttpStatus.INTERNAL_SERVER_ERROR),
  LEVEL_UP_COMMAND_INVALID("LEVEL_007", "Invalid level up command", HttpStatus.BAD_REQUEST),

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
      "TOKEN_003",
      "Authentication security issue detected. Please log in again to continue.",
      HttpStatus.FORBIDDEN // 403
      ),
  TOKEN_HASHING_FAILED(
      "TOKEN_004", "Failed to hash token", HttpStatus.INTERNAL_SERVER_ERROR // 500
      ),

  TOKEN_ENCRYPTION_FAILED(
      "TOKEN_005", "Failed to encrypt/decrypt token", HttpStatus.INTERNAL_SERVER_ERROR // 500
      ),

  // ========================================
  // Wallet Errors (WALLET_xxx)
  // ========================================
  INVALID_WALLET_ADDRESS(
      "WALLET_001", "Invalid wallet address format", HttpStatus.BAD_REQUEST // 400
      ),

  WALLET_ALREADY_LINKED(
      "WALLET_002", "This user already has his/her wallet address", HttpStatus.CONFLICT // 409
      ),

  WALLET_NOT_CONNECTED(
      "WALLET_003", "No wallet connected to this account", HttpStatus.BAD_REQUEST // 400
      ),

  WALLET_NOT_FOUND(
      "WALLET_004", "Wallet not found", HttpStatus.NOT_FOUND // 404
      ),

  WALLET_ALREADY_EXISTS(
      "WALLET_005", "Wallet already exists in system", HttpStatus.CONFLICT // 409
      ),

  WALLET_UNAUTHORIZED_ACCESS(
      "WALLET_006", "Unauthorized access", HttpStatus.UNAUTHORIZED // 401
      ),

  WALLET_IN_BLACKLIST(
      "WALLET_007", "Requested wallet is in block", HttpStatus.BAD_REQUEST // 400
      ),

  // ========================================
  // Web3 Errors (WEB3_xxx)
  // ========================================
  WEB3_INVALID_INPUT("WEB3_001", "Invalid web3 input", HttpStatus.BAD_REQUEST),
  WEB3_TRANSACTION_NOT_FOUND("WEB3_002", "Web3 transaction not found", HttpStatus.NOT_FOUND),
  WEB3_TRANSACTION_STATE_INVALID("WEB3_003", "Invalid web3 transaction state", HttpStatus.CONFLICT),
  WEB3_TREASURY_PRIVATE_KEY_INVALID(
      "WEB3_004", "Invalid treasury private key format", HttpStatus.BAD_REQUEST),
  AUTH_EXPIRED("WEB3_005", "Authorization expired", HttpStatus.BAD_REQUEST),
  AUTH_NONCE_MISMATCH("WEB3_006", "Authority nonce mismatch", HttpStatus.CONFLICT),
  DELEGATE_NOT_ALLOWLISTED(
      "WEB3_007", "Delegate target is not allowlisted", HttpStatus.BAD_REQUEST),
  SPONSOR_GAS_LIMIT_EXCEEDED("WEB3_008", "Sponsor gas limit exceeded", HttpStatus.BAD_REQUEST),
  SPONSOR_DAILY_LIMIT_EXCEEDED(
      "WEB3_009", "Sponsor daily limit exceeded", HttpStatus.TOO_MANY_REQUESTS),
  SPONSOR_AMOUNT_LIMIT_EXCEEDED(
      "WEB3_010", "Sponsor amount limit exceeded", HttpStatus.BAD_REQUEST),
  IDEMPOTENCY_CONFLICT("WEB3_011", "Idempotency conflict", HttpStatus.CONFLICT),
  WEB3_CONFIG_INVALID("WEB3_012", "Invalid web3 configuration", HttpStatus.INTERNAL_SERVER_ERROR),

  // ========================================
  // Challenge Errors (CHALLENGE_xxx)
  // ========================================
  CHALLENGE_NOT_FOUND_OR_EXPIRED(
      "CHALLENGE_001", "Challenge not found or expired", HttpStatus.BAD_REQUEST // 400
      ),

  CHALLENGE_ALREADY_USED(
      "CHALLENGE_002", "This challenge has already been used", HttpStatus.BAD_REQUEST // 400
      ),

  SIGNATURE_VERIFICATION_FAILED(
      "CHALLENGE_003", "Signature verification failed", HttpStatus.UNAUTHORIZED // 401
      ),

  CHALLENGE_WALLET_MISMATCH("CHALLENGE_004", "Wallet address mismatch", HttpStatus.UNAUTHORIZED),
  CHALLENGE_USERID_MISMATCH("CHALLENGE_005", "User id mismatch", HttpStatus.UNAUTHORIZED),

  // ========================================
  // Signature Errors (SIGNATURE_XXX)
  // ========================================

  SIGNATURE_INVALID(
      "SIGNATURE_001", "Invalid signature", HttpStatus.BAD_REQUEST // 400
      ),

  // ========================================
  // Location Errors (LOCATION_xxx)
  // ========================================
  COORDINATE_INVALID("LOCATION_001", "Coordinate is invalid", HttpStatus.BAD_REQUEST),

  GEOCODING_FAILED("LOCATION_002", "Geocoding is failed", HttpStatus.INTERNAL_SERVER_ERROR),

  REV_GEOCODING_FAILED(
      "LOCATION_003", "Reverse Geocoding is failed", HttpStatus.INTERNAL_SERVER_ERROR),

  MISSING_LOCATION_FIELD("LOCATION_004", "Missing location field", HttpStatus.BAD_REQUEST),

  LOCATION_NOT_FOUND("LOCATION_005", "Location not found", HttpStatus.NOT_FOUND),

  LOCATION_ALREADY_DELETED("LOCATION_006", "Location already deleted", HttpStatus.CONFLICT),

  // ========================================
  // Validation Errors (VALIDATION_xxx)
  // ========================================
  INVALID_INPUT(
      "VALIDATION_001", "Invalid input data", HttpStatus.BAD_REQUEST // 400
      ),

  MISSING_REQUIRED_FIELD(
      "VALIDATION_002", "Required field is missing", HttpStatus.BAD_REQUEST // 400
      ),

  RESOURCE_NOT_FOUND(
      "VALIDATION_003", "Resource not found", HttpStatus.NOT_FOUND // 404
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
      ),

  DATA_INTEGRITY_VIOLATION(
      "INTERNAL_004", "A data conflict occurred. Please try again.", HttpStatus.CONFLICT // 409
      ),
  // ========================================
  // Post Errors (POST_xxx)
  // ========================================
  POST_NOT_FOUND(
      "POST_001", "Post not found", HttpStatus.NOT_FOUND // 404
      ),

  POST_UNAUTHORIZED(
      "POST_002", "Unauthorized access to post", HttpStatus.FORBIDDEN // 403
      ),

  INVALID_POST_INPUT(
      "POST_003", "Invalid post input", HttpStatus.BAD_REQUEST // 400
      ),
  // ========================================
  // Comment Errors (COMMENT_xxx)
  // ========================================
  COMMENT_NOT_FOUND(
      "COMMENT_001", "Comment not found", HttpStatus.NOT_FOUND // 404
      ),

  COMMENT_UNAUTHORIZED(
      "COMMENT_002", "Unauthorized access to comment", HttpStatus.FORBIDDEN // 403
      ),

  CANNOT_UPDATE_DELETED_COMMENT(
      "COMMENT_003", "Cannot update a deleted comment", HttpStatus.BAD_REQUEST // 400
      ),

  PARENT_COMMENT_NOT_FOUND(
      "COMMENT_004", "Parent comment not found", HttpStatus.NOT_FOUND // 404
      ),

  COMMENT_POST_MISMATCH(
      "COMMENT_005", "Comment does not belong to the specified post", HttpStatus.BAD_REQUEST // 400
      ),
  COMMENT_TOO_LONG(
      "COMMENT_006", "Comment length must be within 1000 characters", HttpStatus.BAD_REQUEST // 400
      ),
  INVALID_COMMENT_HARD_DELETE_CONFIG(
      "COMMENT_007",
      "Invalid comment hard-delete configuration",
      HttpStatus.INTERNAL_SERVER_ERROR // 500
      ),
  // ========================================
  // Answer Errors (ANSWER_xxx)
  // ========================================
  ANSWER_NOT_FOUND(
      "ANSWER_001", "Answer not found", HttpStatus.NOT_FOUND // 404
      ),

  ANSWER_UNAUTHORIZED(
      "ANSWER_002", "Unauthorized access to answer", HttpStatus.FORBIDDEN // 403
      ),

  CANNOT_ANSWER_OWN_POST(
      "ANSWER_003", "Cannot write an answer on your own post", HttpStatus.BAD_REQUEST // 400
      ),

  CANNOT_ANSWER_SOLVED_POST(
      "ANSWER_004", "Cannot write an answer on a solved post", HttpStatus.BAD_REQUEST // 400
      ),

  CANNOT_UPDATE_ACCEPTED_ANSWER(
      "ANSWER_005", "Cannot update an accepted answer", HttpStatus.BAD_REQUEST // 400
      ),

  CANNOT_DELETE_ACCEPTED_ANSWER(
      "ANSWER_006", "Cannot delete an accepted answer", HttpStatus.BAD_REQUEST // 400
      ),

  ANSWER_POST_MISMATCH(
      "ANSWER_007", "Answer does not belong to the specified post", HttpStatus.BAD_REQUEST // 400
      ),

  REQUIRE_USER_LOGIN(
      "ANSWER_008", "User login is required", HttpStatus.UNAUTHORIZED // 401
      ),
  // ========================================
  // Image Errors (IMAGE_xxx)
  // ========================================
  IMAGE_NOT_FOUND("IMAGE_001", "Image not found", HttpStatus.NOT_FOUND),
  IMAGE_STATUS_INVALID("IMAGE_002", "Image status transition is not allowed", HttpStatus.CONFLICT),
  IMAGE_LAMBDA_UNAUTHORIZED("IMAGE_003", "Invalid lambda webhook secret", HttpStatus.UNAUTHORIZED),
  IMAGE_COUNT_EXCEEDED(
      "IMAGE_004", "Image count exceeds the allowed limit", HttpStatus.BAD_REQUEST),
  IMAGE_INVALID_EXTENSION("IMAGE_005", "Unsupported image file extension", HttpStatus.BAD_REQUEST),
  IMAGE_REF_TYPE_INVALID("IMAGE_006", "Image ref type invalid", HttpStatus.BAD_REQUEST),
  IMAGE_FILE_NAME_INVALID("IMAGE_007", "Image file name invalid", HttpStatus.BAD_REQUEST),
  IMAGE_VIRTUAL_REF_TYPE_CANNOT_BUILD_OBJECT_KEY(
      "IMAGE_008", "Virtual ref type cannot build object key", HttpStatus.INTERNAL_SERVER_ERROR),
  IMAGE_ILLEGAL_OWNERSHIP("IMAGE_009", "Image is not belongs to the user", HttpStatus.FORBIDDEN),
  // ========================================
  // Verification Errors (VERIFICATION_xxx)
  // ========================================
  VERIFICATION_INVALID_TMP_OBJECT_KEY(
      "VERIFICATION_001", "Invalid tmp object key", HttpStatus.BAD_REQUEST),
  VERIFICATION_INVALID_IMAGE_EXTENSION(
      "VERIFICATION_002", "Invalid image extension for verification", HttpStatus.BAD_REQUEST),
  VERIFICATION_UPLOAD_NOT_FOUND("VERIFICATION_003", "Upload not found", HttpStatus.NOT_FOUND),
  VERIFICATION_UPLOAD_FORBIDDEN(
      "VERIFICATION_004", "Upload does not belong to user", HttpStatus.FORBIDDEN),
  VERIFICATION_KIND_MISMATCH(
      "VERIFICATION_005", "Verification kind does not match existing request", HttpStatus.CONFLICT),
  VERIFICATION_ALREADY_COMPLETED_TODAY(
      "VERIFICATION_006", "Workout already completed today", HttpStatus.CONFLICT),
  VERIFICATION_NOT_FOUND("VERIFICATION_007", "Verification not found", HttpStatus.NOT_FOUND),
  ;

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
