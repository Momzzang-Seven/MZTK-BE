package momzzangseven.mztkbe.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("ErrorCode marketplace Web3 계약 테스트")
class ErrorCodeMarketplaceTest {

  @Test
  @DisplayName("marketplace Web3 사용자 복구/실행 에러는 안정적인 HTTP status를 갖는다")
  void marketplaceWeb3ErrorCodes_haveStableHttpStatuses() {
    Map<ErrorCode, HttpStatus> expected =
        Map.ofEntries(
            Map.entry(ErrorCode.MARKETPLACE_WEB3_DISABLED, HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry(ErrorCode.MARKETPLACE_SWITCH_WALLET_REQUIRED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.MARKETPLACE_DEADLINE_REFUND_REQUIRED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.MARKETPLACE_DEADLINE_EXECUTION_WINDOW_EXPIRED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.MARKETPLACE_INSUFFICIENT_ALLOWANCE, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.MARKETPLACE_INSUFFICIENT_TOKEN_BALANCE, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.MARKETPLACE_CONFIRMED_REPAIR_REQUIRED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.MARKETPLACE_APPROVAL_WINDOW_EXPIRED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.MARKETPLACE_STALE_SIGN_REQUEST, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.MARKETPLACE_EXECUTION_NOT_OWNED, HttpStatus.FORBIDDEN),
            Map.entry(ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.MARKETPLACE_CANNOT_BUY_OWN_CLASS, HttpStatus.CONFLICT));

    expected.forEach((code, status) -> assertThat(code.getHttpStatus()).isEqualTo(status));
  }
}
