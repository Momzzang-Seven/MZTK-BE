package momzzangseven.mztkbe.global.error.treasury;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link TreasuryWalletStateException} — verifies ErrorCode wiring, HTTP status,
 * and the null-message fallback inherited from {@link BusinessException}.
 *
 * <p>Covers test cases M-104 .. M-105 (Commit 1-6, Group H).
 */
@DisplayName("TreasuryWalletStateException 단위 테스트")
class TreasuryWalletStateExceptionTest {

  // =========================================================================
  // Section H — ErrorCode wiring
  // =========================================================================

  @Nested
  @DisplayName("H. ErrorCode 연결 검증")
  class ErrorCodeWiring {

    @Test
    @DisplayName("[M-104] 생성자 — TREASURY_001 코드 및 HTTP 409 바인딩")
    void constructor_carriesTreasury001AndHttp409() {
      // given / when
      TreasuryWalletStateException ex =
          new TreasuryWalletStateException("test invalid transition");

      // then
      assertThat(ex.getMessage()).isEqualTo("test invalid transition");
      assertThat(ex.getCode()).isEqualTo("TREASURY_001");
      assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(ex).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[M-105] null 메시지 → ErrorCode 기본 메시지로 대체")
    void nullMessage_fallsBackToErrorCodeDefaultMessage() {
      // given / when
      TreasuryWalletStateException ex = new TreasuryWalletStateException(null);

      // then
      assertThat(ex.getMessage())
          .isEqualTo(ErrorCode.TREASURY_WALLET_INVALID_STATE.getMessage());
      assertThat(ex.getMessage())
          .isEqualTo("Treasury wallet is not in a state that allows the requested transition");
      assertThat(ex.getCode()).isEqualTo("TREASURY_001");
    }
  }
}
