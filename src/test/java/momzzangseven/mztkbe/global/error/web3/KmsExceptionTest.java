package momzzangseven.mztkbe.global.error.web3;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link KmsSignFailedException} and {@link KmsKeyDescribeFailedException} —
 * verifies ErrorCode wiring, HTTP status, and two-argument constructor cause-preservation.
 *
 * <p>Covers test cases M-50 through M-52 (Commit 1-3, Group F).
 */
@DisplayName("KMS 예외 단위 테스트")
class KmsExceptionTest {

  // =========================================================================
  // Section F — KmsSignFailedException
  // =========================================================================

  @Nested
  @DisplayName("F-1. KmsSignFailedException ErrorCode 연결 검증")
  class KmsSignFailedExceptionWiring {

    @Test
    @DisplayName("[M-50] KmsSignFailedException — WEB3_017 코드 및 HTTP 500 바인딩")
    void singleMessageConstructor_carriesWEB3_017AndHttp500() {
      // given / when
      KmsSignFailedException ex = new KmsSignFailedException("sign failed");

      // then
      assertThat(ex.getMessage()).isEqualTo("sign failed");
      assertThat(ex.getCode()).isEqualTo("WEB3_017");
      assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(ex).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[M-52a] KmsSignFailedException 두 인수 생성자 — 원인 예외 보존")
    void twoArgConstructor_preservesCause() {
      // given
      RuntimeException cause = new RuntimeException("aws sdk error");

      // when
      KmsSignFailedException signEx = new KmsSignFailedException("wrapped", cause);

      // then
      assertThat(signEx.getCause()).isSameAs(cause);
      assertThat(signEx.getMessage()).isEqualTo("wrapped");
      assertThat(signEx.getCode()).isEqualTo("WEB3_017");
    }
  }

  // =========================================================================
  // Section F — KmsKeyDescribeFailedException
  // =========================================================================

  @Nested
  @DisplayName("F-2. KmsKeyDescribeFailedException ErrorCode 연결 검증")
  class KmsKeyDescribeFailedExceptionWiring {

    @Test
    @DisplayName("[M-51] KmsKeyDescribeFailedException — WEB3_018 코드 및 HTTP 500 바인딩")
    void singleMessageConstructor_carriesWEB3_018AndHttp500() {
      // given / when
      KmsKeyDescribeFailedException ex = new KmsKeyDescribeFailedException("describe failed");

      // then
      assertThat(ex.getMessage()).isEqualTo("describe failed");
      assertThat(ex.getCode()).isEqualTo("WEB3_018");
      assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(ex).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[M-52b] KmsKeyDescribeFailedException 두 인수 생성자 — 원인 예외 보존")
    void twoArgConstructor_preservesCause() {
      // given
      RuntimeException cause = new RuntimeException("aws sdk error");

      // when
      KmsKeyDescribeFailedException descEx = new KmsKeyDescribeFailedException("wrapped", cause);

      // then
      assertThat(descEx.getCause()).isSameAs(cause);
      assertThat(descEx.getMessage()).isEqualTo("wrapped");
      assertThat(descEx.getCode()).isEqualTo("WEB3_018");
    }
  }
}
