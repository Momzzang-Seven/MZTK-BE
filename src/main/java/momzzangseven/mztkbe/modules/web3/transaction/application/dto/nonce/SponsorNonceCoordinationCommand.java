package momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

public record SponsorNonceCoordinationCommand(
    long chainId,
    String fromAddress,
    long chainPendingNonce,
    long chainLatestNonce,
    Long mainPendingNonce,
    Long subPendingNonce,
    Long mainLatestNonce,
    Long subLatestNonce,
    int openWindowSize,
    Long transactionId,
    String attemptIdempotencyKey,
    LocalDateTime now) {

  public SponsorNonceCoordinationCommand {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    fromAddress = EvmAddress.of(fromAddress).value();
    if (chainPendingNonce < 0 || chainLatestNonce < 0) {
      throw new Web3InvalidInputException("chain nonces must be >= 0");
    }
    if (openWindowSize <= 0) {
      throw new Web3InvalidInputException("openWindowSize must be positive");
    }
    if (transactionId != null && transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (transactionId != null && now == null) {
      throw new Web3InvalidInputException("now is required when transactionId is set");
    }
  }

  public boolean shouldReserveIssuedNonce() {
    return transactionId != null;
  }
}
