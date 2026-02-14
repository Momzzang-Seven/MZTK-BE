package momzzangseven.mztkbe.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/** Standard API response wrapper. */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
  private final String status;
  private final String message;
  private final T data;
  private final String code;
  private final Boolean retryable;

  private ApiResponse(String status, String message, T data, String code, Boolean retryable) {
    this.status = status;
    this.message = message;
    this.data = data;
    this.code = code;
    this.retryable = retryable;
  }

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>("SUCCESS", null, data, null, null);
  }

  public static <T> ApiResponse<T> success(String message, T data) {
    return new ApiResponse<>("SUCCESS", message, data, null, null);
  }

  public static <T> ApiResponse<T> error(String message, String code) {
    return new ApiResponse<>("FAIL", message, null, code, null);
  }

  public static <T> ApiResponse<T> error(String message, String code, Boolean retryable) {
    return new ApiResponse<>("FAIL", message, null, code, retryable);
  }

  public static <T> ApiResponse<T> error(String message, String code, T data) {
    return new ApiResponse<>("FAIL", message, data, code, null);
  }

  public static <T> ApiResponse<T> error(String message, String code, T data, Boolean retryable) {
    return new ApiResponse<>("FAIL", message, data, code, retryable);
  }
}
