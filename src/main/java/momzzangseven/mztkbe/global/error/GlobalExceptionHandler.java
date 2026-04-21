package momzzangseven.mztkbe.global.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.token.TokenException;
import momzzangseven.mztkbe.global.error.verification.VerificationAlreadyCompletedTodayException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

  @ExceptionHandler(Web3TransferException.class)
  public ResponseEntity<ApiResponse<Void>> handleWeb3TransferException(Web3TransferException ex) {
    log.warn(
        "Web3 transfer exception: {} (code: {}, status: {}, retryable: {})",
        ex.getMessage(),
        ex.getCode(),
        ex.getHttpStatus(),
        ex.isRetryable());

    return ResponseEntity.status(ex.getHttpStatus())
        .body(ApiResponse.error(ex.getMessage(), ex.getCode(), ex.isRetryable()));
  }

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

  @ExceptionHandler(VerificationAlreadyCompletedTodayException.class)
  public ResponseEntity<ApiResponse<VerificationAlreadyCompletedTodayException.ErrorData>>
      handleVerificationAlreadyCompletedTodayException(
          VerificationAlreadyCompletedTodayException ex) {
    log.warn(
        "Business exception: {} (code: {}, status: {})",
        ex.getMessage(),
        ex.getCode(),
        ex.getHttpStatus());

    return ResponseEntity.status(ex.getHttpStatus())
        .body(ApiResponse.error(ex.getMessage(), ex.getCode(), ex.getData()));
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
  public ResponseEntity<ApiResponse<?>> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    if (isModelAttributeValidation(ex)
        && hasMissingRequiredFields(ex.getBindingResult().getFieldErrors(), request)) {
      ErrorCode errorCode = ErrorCode.MISSING_REQUIRED_FIELD;
      return ResponseEntity.status(errorCode.getHttpStatus())
          .body(ApiResponse.error(errorCode.getMessage(), errorCode.getCode()));
    }

    Map<String, String> fieldErrors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

    ErrorCode errorCode = ErrorCode.INVALID_INPUT;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error("Validation failed", errorCode.getCode(), fieldErrors));
  }

  /**
   * Handle validation failures from {@code @ModelAttribute} bound query/path requests.
   *
   * <p>Missing required query parameters are mapped to {@code VALIDATION_002}; all other binding or
   * bean-validation failures are mapped to {@code VALIDATION_001}.
   */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<ApiResponse<?>> handleBindException(
      BindException ex, HttpServletRequest request) {
    boolean hasMissingRequiredField =
        ex.getBindingResult().getFieldErrors().stream()
            .anyMatch(error -> isMissingRequiredField(error, request));

    if (hasMissingRequiredField) {
      ErrorCode errorCode = ErrorCode.MISSING_REQUIRED_FIELD;
      return ResponseEntity.status(errorCode.getHttpStatus())
          .body(ApiResponse.error(errorCode.getMessage(), errorCode.getCode()));
    }

    Map<String, String> fieldErrors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

    ErrorCode errorCode = ErrorCode.INVALID_INPUT;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error("Validation failed", errorCode.getCode(), fieldErrors));
  }

  /** Handle missing required request headers (e.g., X-Lambda-Webhook-Secret not present). */
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeaderException(
      MissingRequestHeaderException ex) {
    ErrorCode errorCode = ErrorCode.MISSING_REQUIRED_FIELD;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(ex.getMessage(), errorCode.getCode()));
  }

  /** Handle missing required query parameters. */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
      MissingServletRequestParameterException ex) {
    ErrorCode errorCode = ErrorCode.MISSING_REQUIRED_FIELD;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(ex.getMessage(), errorCode.getCode()));
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

  /** Handle request parameter/path variable type mismatches (e.g., invalid enum query values). */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
      MethodArgumentTypeMismatchException ex) {
    ErrorCode errorCode = ErrorCode.INVALID_INPUT;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error("Invalid request parameter type", errorCode.getCode()));
  }

  private boolean isMissingRequiredField(FieldError error, HttpServletRequest request) {
    if (!"NotNull".equals(error.getCode())
        && !"NotEmpty".equals(error.getCode())
        && !"NotBlank".equals(error.getCode())) {
      return false;
    }
    return !request.getParameterMap().containsKey(resolveRequestParameterName(error.getField()));
  }

  private boolean hasMissingRequiredFields(
      Iterable<FieldError> fieldErrors, HttpServletRequest request) {
    for (FieldError error : fieldErrors) {
      if (isMissingRequiredField(error, request)) {
        return true;
      }
    }
    return false;
  }

  private String resolveRequestParameterName(String field) {
    int bracketIndex = field.indexOf('[');
    if (bracketIndex >= 0) {
      return field.substring(0, bracketIndex);
    }
    int nestedFieldIndex = field.indexOf('.');
    if (nestedFieldIndex >= 0) {
      return field.substring(0, nestedFieldIndex);
    }
    return field;
  }

  private boolean isModelAttributeValidation(MethodArgumentNotValidException ex) {
    return ex.getParameter().hasParameterAnnotation(ModelAttribute.class);
  }

  /** Handle missing endpoint/static resource requests as 404 instead of 500. */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
      NoResourceFoundException ex) {
    ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(errorCode.getMessage(), errorCode.getCode()));
  }

  /**
   * Handle JSON deserialization errors (e.g., invalid enum values, type mismatches).
   *
   * <p>This occurs when the client sends JSON that cannot be parsed into the expected types, such
   * as sending "foo" for a UserRole enum that only accepts [USER, TRAINER, ADMIN].
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException ex) {
    log.warn("JSON deserialization error: {}", ex.getMessage());

    String userFriendlyMessage = "Invalid request format. Please check your input values.";

    // Extract more specific error message if available
    if (ex.getMessage() != null) {
      if (ex.getMessage().contains("not one of the values accepted for Enum")) {
        userFriendlyMessage = "Invalid enum value. Please check allowed values.";
      } else if (ex.getMessage().contains("Cannot deserialize")) {
        userFriendlyMessage = "Invalid data format. Please check your request body.";
      }
    }

    ErrorCode errorCode = ErrorCode.INVALID_INPUT;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(userFriendlyMessage, errorCode.getCode()));
  }

  // ========================================
  // Generic Exceptions
  // ========================================

  /**
   * Handle DB unique constraint / FK violations.
   *
   * <p>Covers cases such as UUID collision on {@code tmp_object_key}. Returns 409 Conflict so the
   * client can distinguish this from a generic 500 and retry if appropriate.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
      DataIntegrityViolationException ex) {
    log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
    ErrorCode errorCode = ErrorCode.DATA_INTEGRITY_VIOLATION;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(errorCode.getMessage(), errorCode.getCode()));
  }

  /**
   * Handle optimistic locking conflicts (e.g., concurrent class updates).
   *
   * <p>Triggered when JPA {@code @Version} detects a stale entity. Returns 409 with {@code
   * MARKETPLACE_024} so the client knows to retry with a fresh read.
   */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailureException(
      OptimisticLockingFailureException ex) {
    log.warn("Optimistic locking conflict: {}", ex.getMessage());
    ErrorCode errorCode = ErrorCode.MARKETPLACE_CONCURRENT_UPDATE;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.error(errorCode.getMessage(), errorCode.getCode()));
  }

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
