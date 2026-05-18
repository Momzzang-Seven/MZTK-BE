package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record WalletApprovalExecutionSupport(
    long chainId,
    String delegateTarget,
    long authorityNonce,
    String authorizationPayloadHash,
    int ttlSeconds) {

  public WalletApprovalExecutionSupport {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    if (delegateTarget == null || delegateTarget.isBlank()) {
      throw new Web3InvalidInputException("delegateTarget is required");
    }
    if (authorityNonce < 0) {
      throw new Web3InvalidInputException("authorityNonce must be >= 0");
    }
    if (authorizationPayloadHash == null || authorizationPayloadHash.isBlank()) {
      throw new Web3InvalidInputException("authorizationPayloadHash is required");
    }
    if (ttlSeconds <= 0) {
      throw new Web3InvalidInputException("ttlSeconds must be positive");
    }
  }
}
