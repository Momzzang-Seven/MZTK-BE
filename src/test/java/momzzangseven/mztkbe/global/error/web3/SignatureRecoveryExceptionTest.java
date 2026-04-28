package momzzangseven.mztkbe.global.error.web3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link SignatureRecoveryException} — verifies ErrorCode wiring and constructor
 * behaviour.
 */
@DisplayName("SignatureRecoveryException 단위 테스트")
class SignatureRecoveryExceptionTest {

  // =========================================================================
  // Section G — ErrorCode wiring
  // =========================================================================

  @Nested
  @DisplayName("G. ErrorCode 연결 검증")
  class ErrorCodeWiring {

    @Test
    @DisplayName("[M-20] 단일 메시지 생성자 — WEB3_SIGNATURE_RECOVERY_FAILED 코드 바인딩")
    void singleMessageConstructor_carriesCorrectErrorCode() {
      // given / when
      SignatureRecoveryException ex = new SignatureRecoveryException("test error");

      // then
      assertThat(ex.getMessage()).isEqualTo("test error");
      assertThat(ex.getCode()).isEqualTo("WEB3_016");
      assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(ex).isInstanceOf(BusinessException.class);
      assertThat(ex.getCode()).isEqualTo(ErrorCode.WEB3_SIGNATURE_RECOVERY_FAILED.getCode());
    }

    @Test
    @DisplayName("[M-21] 두 인수 생성자 — 원인 예외 보존")
    void twoArgConstructor_preservesCause() {
      // given
      IOException cause = new IOException("underlying io error");

      // when
      SignatureRecoveryException ex = new SignatureRecoveryException("wrapper message", cause);

      // then
      assertThat(ex.getMessage()).isEqualTo("wrapper message");
      assertThat(ex.getCause()).isSameAs(cause);
      assertThat(ex.getCause()).isInstanceOf(IOException.class);
      assertThat(ex.getCode()).isEqualTo("WEB3_016");
    }

    @Test
    @DisplayName("[M-22] null 메시지 → ErrorCode 기본 메시지로 대체")
    void nullMessage_fallsBackToErrorCodeMessage() {
      // given / when
      SignatureRecoveryException ex = new SignatureRecoveryException(null);

      // then
      assertThat(ex.getMessage()).isEqualTo(ErrorCode.WEB3_SIGNATURE_RECOVERY_FAILED.getMessage());
      assertThat(ex.getMessage())
          .isEqualTo("Failed to recover Ethereum address from KMS signature");
      assertThat(ex.getCode()).isEqualTo("WEB3_016");
    }
  }
}
