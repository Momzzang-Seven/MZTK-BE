package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;

/** Shared wallet-registration interpretation for receipt-timeout transaction state. */
public final class WalletRegistrationReceiptTimeout {

  public static final String ERROR_CODE = "RECEIPT_TIMEOUT";
  public static final String ERROR_REASON =
      "Receipt was not confirmed before the backend polling window timed out.";
  public static final String TRANSACTION_STATUS = "UNCONFIRMED";

  private WalletRegistrationReceiptTimeout() {}

  public static boolean isCurrent(WalletApprovalExecutionStateView state) {
    return state != null && TRANSACTION_STATUS.equals(state.transactionStatus());
  }

  public static boolean isRecordedOn(WalletRegistrationSession session) {
    return session != null && ERROR_CODE.equals(session.getLastErrorCode());
  }

  public static boolean approvalTtlRemains(WalletRegistrationSession session, LocalDateTime now) {
    return session.getApprovalExpiresAt() != null && session.getApprovalExpiresAt().isAfter(now);
  }
}
