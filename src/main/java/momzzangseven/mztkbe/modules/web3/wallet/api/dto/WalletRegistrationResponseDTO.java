package momzzangseven.mztkbe.modules.web3.wallet.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;

/** API response for wallet registration status and approval retry endpoints. */
public record WalletRegistrationResponseDTO(
    String registrationId,
    String status,
    String walletAddress,
    Long registeredWalletId,
    String latestExecutionIntentId,
    String latestExecutionStatus,
    LocalDateTime approvalExpiresAt,
    Transaction transaction,
    String lastErrorCode,
    String lastErrorReason,
    String signRequestUnavailableReason,
    String nextAction,
    RegisterWalletResponseDTO.Web3 web3) {

  public static WalletRegistrationResponseDTO from(WalletRegistrationStatusResult result) {
    return new WalletRegistrationResponseDTO(
        result.registrationId(),
        result.status().name(),
        result.walletAddress(),
        result.registeredWalletId(),
        result.latestExecutionIntentId(),
        result.latestExecutionStatus(),
        result.approvalExpiresAt(),
        Transaction.from(result.transaction()),
        result.lastErrorCode(),
        result.lastErrorReason(),
        result.signRequestUnavailableReason(),
        result.nextAction().name(),
        RegisterWalletResponseDTO.Web3.from(result.web3()));
  }

  public record Transaction(Long transactionId, String transactionStatus, String txHash) {

    static Transaction from(
        momzzangseven.mztkbe.modules.web3.wallet.application.dto
                .WalletRegistrationTransactionSummary
            summary) {
      if (summary == null) {
        return null;
      }
      return new Transaction(
          summary.transactionId(), summary.transactionStatus(), summary.txHash());
    }
  }
}
