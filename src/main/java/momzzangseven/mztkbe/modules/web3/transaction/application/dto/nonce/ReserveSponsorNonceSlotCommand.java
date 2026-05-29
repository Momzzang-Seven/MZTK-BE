package momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

public record ReserveSponsorNonceSlotCommand(
    long chainId,
    String fromAddress,
    long nonce,
    Long transactionId,
    String idempotencyKey,
    LocalDateTime now) {

  public ReserveSponsorNonceSlotCommand {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    fromAddress = EvmAddress.of(fromAddress).value();
    if (nonce < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new Web3InvalidInputException("idempotencyKey is required");
    }
    if (now == null) {
      throw new Web3InvalidInputException("now is required");
    }
  }
}
