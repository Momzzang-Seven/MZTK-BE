package momzzangseven.mztkbe.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.global.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
  }

  @Test
  void handleHttpMessageNotReadableException_returnsEnumFriendlyMessage_whenEnumTextIncluded() {
    HttpMessageNotReadableException ex =
        new HttpMessageNotReadableException("value not one of the values accepted for Enum");

    ResponseEntity<ApiResponse<Void>> response = handler.handleHttpMessageNotReadableException(ex);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage())
        .isEqualTo("Invalid enum value. Please check allowed values.");
    assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.INVALID_INPUT.getCode());
  }

  @Test
  void
      handleHttpMessageNotReadableException_returnsFormatFriendlyMessage_whenDeserializeTextIncluded() {
    HttpMessageNotReadableException ex =
        new HttpMessageNotReadableException("Cannot deserialize value of type");

    ResponseEntity<ApiResponse<Void>> response = handler.handleHttpMessageNotReadableException(ex);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage())
        .isEqualTo("Invalid data format. Please check your request body.");
  }

  @Test
  void handleHttpMessageNotReadableException_returnsDefaultMessage_whenMessageNull() {
    HttpMessageNotReadableException ex = new HttpMessageNotReadableException((String) null);

    ResponseEntity<ApiResponse<Void>> response = handler.handleHttpMessageNotReadableException(ex);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getMessage())
        .isEqualTo("Invalid request format. Please check your input values.");
  }
}
