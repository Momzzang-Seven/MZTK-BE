package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import java.time.LocalDateTime;

/** Advances wallet registration recovery ordering without mutating lifecycle state. */
public interface AdvanceWalletRegistrationRecoveryCursorPort {

  void advanceReceiptTimeoutFailedRecoveryCursor(String registrationId, LocalDateTime checkedAt);
}
