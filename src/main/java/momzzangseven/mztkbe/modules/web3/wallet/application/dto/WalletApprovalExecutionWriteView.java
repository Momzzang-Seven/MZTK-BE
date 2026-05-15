package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import java.time.LocalDateTime;

/**
 * Wallet-module owned projection of approval execution write payload.
 *
 * <p>This keeps wallet registration decoupled from shared execution DTOs while exposing the signing
 * contract needed by the API response.
 */
public record WalletApprovalExecutionWriteView(
    Resource resource,
    String actionType,
    ExecutionIntent executionIntent,
    Execution execution,
    SignRequest signRequest,
    String signRequestUnavailableReason,
    boolean existing) {

  public static WalletApprovalExecutionWriteView from(WalletApprovalExecutionIntentResult result) {
    if (result == null) {
      return null;
    }
    return new WalletApprovalExecutionWriteView(
        new Resource(result.resource().type(), result.resource().id(), result.resource().status()),
        result.actionType(),
        new ExecutionIntent(
            result.executionIntent().id(),
            result.executionIntent().status(),
            result.executionIntent().expiresAt(),
            result.executionIntent().expiresAtEpochSeconds()),
        new Execution(result.execution().mode(), result.execution().signCount()),
        SignRequest.from(result.signRequest()),
        null,
        result.existing());
  }

  public static WalletApprovalExecutionWriteView from(WalletApprovalExecutionStateView state) {
    if (state == null) {
      return null;
    }
    return new WalletApprovalExecutionWriteView(
        new Resource(state.resourceType(), state.resourceId(), state.resourceStatus()),
        state.actionType(),
        new ExecutionIntent(
            state.executionIntentId(),
            state.executionIntentStatus(),
            state.expiresAt(),
            state.expiresAtEpochSeconds()),
        new Execution(state.mode(), state.signCount()),
        SignRequest.from(state.signRequest()),
        state.signRequestUnavailableReason(),
        true);
  }

  public record Resource(String type, String id, String status) {}

  public record ExecutionIntent(
      String id, String status, LocalDateTime expiresAt, long expiresAtEpochSeconds) {}

  public record Execution(String mode, int signCount) {}

  public record SignRequest(Authorization authorization, Submit submit, Transaction transaction) {

    static SignRequest from(WalletApprovalSignRequestBundle request) {
      if (request == null) {
        return null;
      }
      return new SignRequest(
          Authorization.from(request.authorization()),
          Submit.from(request.submit()),
          Transaction.from(request.transaction()));
    }
  }

  public record Authorization(
      Long chainId, String delegateTarget, Long authorityNonce, String payloadHashToSign) {

    static Authorization from(WalletApprovalSignRequestBundle.AuthorizationSignRequest request) {
      if (request == null) {
        return null;
      }
      return new Authorization(
          request.chainId(),
          request.delegateTarget(),
          request.authorityNonce(),
          request.payloadHashToSign());
    }
  }

  public record Submit(String executionDigest, Long deadlineEpochSeconds) {

    static Submit from(WalletApprovalSignRequestBundle.SubmitSignRequest request) {
      if (request == null) {
        return null;
      }
      return new Submit(request.executionDigest(), request.deadlineEpochSeconds());
    }
  }

  public record Transaction(
      Long chainId,
      String fromAddress,
      String toAddress,
      String valueHex,
      String data,
      Long nonce,
      String gasLimitHex,
      String maxPriorityFeePerGasHex,
      String maxFeePerGasHex,
      Long expectedNonce) {

    static Transaction from(WalletApprovalSignRequestBundle.TransactionSignRequest request) {
      if (request == null) {
        return null;
      }
      return new Transaction(
          request.chainId(),
          request.fromAddress(),
          request.toAddress(),
          request.valueHex(),
          request.data(),
          request.nonce(),
          request.gasLimitHex(),
          request.maxPriorityFeePerGasHex(),
          request.maxFeePerGasHex(),
          request.expectedNonce());
    }
  }
}
