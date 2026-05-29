package momzzangseven.mztkbe.modules.web3.transaction.application.port.in;

import java.time.LocalDateTime;

public interface PersistSponsorNonceTransactionStateUseCase {

  void markSigned(SponsorNonceSignedCommand command);

  void markPending(SponsorNoncePendingCommand command);

  void markPendingWithoutSlotTransition(TransactionPendingCommand command);

  void markUnconfirmed(SponsorNonceUnconfirmedCommand command);

  void failTerminalAndDropReservedSlot(SponsorNonceTerminalReservedSlotFailureCommand command);

  void markSignedOperatorReview(SponsorNonceSignedOperatorReviewCommand command);

  void markBroadcastingOperatorReview(SponsorNonceBroadcastingOperatorReviewCommand command);

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

  record TransactionPendingCommand(Long transactionId, String txHash) {}

  record SponsorNonceUnconfirmedCommand(
      Long transactionId,
      long chainId,
      String fromAddress,
      Long nonce,
      String txHash,
      String failureReason,
      LocalDateTime stateChangedAt) {}

  record SponsorNonceTerminalReservedSlotFailureCommand(
      Long transactionId,
      long chainId,
      String fromAddress,
      long nonce,
      Long attemptId,
      String failureReason,
      LocalDateTime stateChangedAt) {}

  record SponsorNonceSignedOperatorReviewCommand(
      Long transactionId,
      long chainId,
      String fromAddress,
      Long nonce,
      String slotTerminalReason,
      String transactionFailureReason,
      LocalDateTime stateChangedAt) {}

  record SponsorNonceBroadcastingOperatorReviewCommand(
      Long transactionId,
      long chainId,
      String fromAddress,
      Long nonce,
      Long attemptId,
      String slotTerminalReason,
      String transactionFailureReason,
      boolean hasRawTx,
      boolean hasTxHash,
      boolean hasSigningEvidence,
      boolean hasBroadcastEvidence,
      LocalDateTime stateChangedAt) {}
}
