package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;

public record RegisterWalletResult(
    String registrationId,
    WalletRegistrationStatus status,
    Long walletId,
    String walletAddress,
    Instant registeredAt,
    WalletApprovalExecutionWriteView web3) {

  public static RegisterWalletResult pending(
      WalletRegistrationSession session, WalletApprovalExecutionWriteView web3) {
    return new RegisterWalletResult(
        session.getPublicId(), session.getStatus(), null, session.getWalletAddress(), null, web3);
  }

  public static RegisterWalletResult from(UserWallet wallet) {
    return new RegisterWalletResult(
        null,
        WalletRegistrationStatus.REGISTERED,
        wallet.getId(),
        wallet.getWalletAddress(),
        wallet.getRegisteredAt(),
        null);
  }
}
