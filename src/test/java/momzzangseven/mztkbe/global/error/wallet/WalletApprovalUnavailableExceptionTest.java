package momzzangseven.mztkbe.global.error.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("WalletApprovalUnavailableException 단위 테스트")
class WalletApprovalUnavailableExceptionTest {

  @Test
  void constructor_carriesWalletApprovalUnavailableCodeAndHttp503() {
    WalletApprovalUnavailableException ex = new WalletApprovalUnavailableException();

    assertThat(ex.getMessage()).isEqualTo(ErrorCode.WALLET_APPROVAL_UNAVAILABLE.getMessage());
    assertThat(ex.getCode()).isEqualTo("WALLET_008");
    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(ex).isInstanceOf(BusinessException.class);
  }

  @Test
  void constructor_withCustomMessage_preservesMessageAndErrorCode() {
    WalletApprovalUnavailableException ex = new WalletApprovalUnavailableException("not ready");

    assertThat(ex.getMessage()).isEqualTo("not ready");
    assertThat(ex.getCode()).isEqualTo("WALLET_008");
    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(ex).isInstanceOf(BusinessException.class);
  }
}
