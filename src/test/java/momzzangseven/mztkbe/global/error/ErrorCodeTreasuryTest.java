package momzzangseven.mztkbe.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for the four new {@link ErrorCode} TREASURY_xxx entries.
 *
 * <p>Covers test cases M-106 .. M-109 (Commit 1-6, Group I). Pre-existing ErrorCode constants are
 * NOT retested here.
 */
@DisplayName("ErrorCode TREASURY_xxx 단위 테스트")
class ErrorCodeTreasuryTest {

  // =========================================================================
  // Section I — TREASURY ErrorCode entries
  // =========================================================================

  @Nested
  @DisplayName("I. TREASURY_xxx ErrorCode 항목 검증")
  class TreasuryErrorCodes {

    @Test
    @DisplayName("[M-106] TREASURY_WALLET_INVALID_STATE — 코드 'TREASURY_001', HTTP 409")
    void treasuryWalletInvalidState_hasCorrectCodeAndStatus() {
      // given
      ErrorCode entry = ErrorCode.TREASURY_WALLET_INVALID_STATE;

      // then
      assertThat(entry.getCode()).isEqualTo("TREASURY_001");
      assertThat(entry.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(entry.getMessage()).isNotBlank();
    }

    @Test
    @DisplayName("[M-107] TREASURY_KEY_PRIVATE_KEY_INVALID — 코드 'TREASURY_002', HTTP 400")
    void treasuryKeyPrivateKeyInvalid_hasCorrectCodeAndStatus() {
      // given
      ErrorCode entry = ErrorCode.TREASURY_KEY_PRIVATE_KEY_INVALID;

      // then
      assertThat(entry.getCode()).isEqualTo("TREASURY_002");
      assertThat(entry.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(entry.getMessage()).isNotBlank();
    }

    @Test
    @DisplayName("[M-108] TREASURY_WALLET_ADDRESS_MISMATCH — 코드 'TREASURY_003', HTTP 500")
    void treasuryWalletAddressMismatch_hasCorrectCodeAndStatus() {
      // given
      ErrorCode entry = ErrorCode.TREASURY_WALLET_ADDRESS_MISMATCH;

      // then
      assertThat(entry.getCode()).isEqualTo("TREASURY_003");
      assertThat(entry.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(entry.getMessage()).isNotBlank();
    }

    @Test
    @DisplayName("[M-109] TREASURY_WALLET_ALREADY_PROVISIONED — 코드 'TREASURY_004', HTTP 409")
    void treasuryWalletAlreadyProvisioned_hasCorrectCodeAndStatus() {
      // given
      ErrorCode entry = ErrorCode.TREASURY_WALLET_ALREADY_PROVISIONED;

      // then
      assertThat(entry.getCode()).isEqualTo("TREASURY_004");
      assertThat(entry.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(entry.getMessage()).isNotBlank();
    }
  }
}
