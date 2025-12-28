package momzzangseven.mztkbe.global.error;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.token.TokenException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the entire application.
 *
 * <p>This class catches all exceptions thrown by every instance and converts them into standardized
 * ApiResponse objects.
 *
 * <p>Exception handling order: 1. Most specific exceptions (e.g., UserNotFoundException) 2.
 * Business exceptions (BusinessException) 3. Validation exceptions
 * (MethodArgumentNotValidException) 4. Generic exceptions (Exception)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  // ========================================
  // Business Exceptions
  // ========================================

  /**
   * Handle all BusinessExceptions.
   *
   * <p>This single handler covers all children of BusinessException.
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
    // Log with error code and HTTP status
    log.warn(
        "Business exception: {} (code: {}, status: {})",
        ex.getMessage(),
        ex.getCode(),
        ex.getHttpStatus());

    // Get HTTP status from ErrorCode
    return ResponseEntity.status(ex.getHttpStatus())
        .body(ApiResponse.error(ex.getMessage(), ex.getCode()));
  }

  /**
   * Handle token-specific business exceptions coming from authentication flows.
   *
   * @param ex token-related business exception
   */
  @ExceptionHandler(TokenException.class)
  public ResponseEntity<ApiResponse<Void>> handleTokenException(TokenException ex) {
    log.warn(
        "Token exception: {} (code: {}, status: {})",
        ex.getMessage(),
        ex.getCode(),
        ex.getHttpStatus());
    // Get HTTP status from ErrorCode
    return ResponseEntity.status(ex.getHttpStatus())
        .body(ApiResponse.error(ex.getMessage(), ex.getCode()));
  }

  /**
   * Handle Bean Validation failures from {@code @Valid} annotated requests.
   *
   * <p>Returns 400 with field error details to make development/debugging easier.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

    ErrorCode errorCode = ErrorCode.INVALID_INPUT;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error("Validation failed", errorCode.getCode(), fieldErrors));
  }

  /** Handle missing cookies (e.g., refresh token cookie not present). */
  @ExceptionHandler(MissingRequestCookieException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingRequestCookieException(
      MissingRequestCookieException ex) {
    ErrorCode errorCode = ErrorCode.MISSING_REQUIRED_FIELD;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(ex.getMessage(), errorCode.getCode()));
  }

  /** Handle malformed client input errors. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
      IllegalArgumentException ex) {
    ErrorCode errorCode = ErrorCode.INVALID_INPUT;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(ex.getMessage(), errorCode.getCode()));
  }

  // ========================================
  // Generic Exceptions
  // ========================================

  /** Handle all other uncaught exceptions. This is a catch-all handler for unexpected errors. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
    log.error("Unexpected exception occurred", ex);

    // Use ErrorCode for internal server error
    ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

    return ResponseEntity.status(errorCode.getHttpStatus()) // ← ErrorCode에서 가져옴!
        .body(ApiResponse.error(errorCode.getMessage(), errorCode.getCode()));
  }
}
