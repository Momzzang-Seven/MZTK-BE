package momzzangseven.mztkbe.modules.web3.transaction.application.port.in;

import java.time.LocalDateTime;

public interface PersistSponsorNonceTransactionStateUseCase {

  void markSigned(SponsorNonceSignedCommand command);

  void markPending(SponsorNoncePendingCommand command);

  void markUnconfirmed(SponsorNonceUnconfirmedCommand command);

  record SponsorNonceSignedCommand(
      Long transactionId,
      long chainId,
      String fromAddress,
      long nonce,
      Long attemptId,
      String signedRawTx,
      String txHash,
      LocalDateTime stateChangedAt) {}

  record SponsorNoncePendingCommand(
      Long transactionId,
      long chainId,
      String fromAddress,
      long nonce,
      Long attemptId,
      String txHash,
      LocalDateTime stateChangedAt) {}

  record SponsorNonceUnconfirmedCommand(
      Long transactionId,
      long chainId,
      String fromAddress,
      Long nonce,
      String txHash,
      String failureReason,
      LocalDateTime stateChangedAt) {}
}
