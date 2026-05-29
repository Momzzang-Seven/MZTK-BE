package momzzangseven.mztkbe.modules.web3.wallet.api.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionWriteView;

public record RegisterWalletResponseDTO(
    String registrationId,
    String status,
    Long walletId,
    String walletAddress,
    Instant registeredAt,
    String nextAction,
    String userMessage,
    String supportMessageKey,
    Web3 web3) {

  public static RegisterWalletResponseDTO from(RegisterWalletResult result) {
    return new RegisterWalletResponseDTO(
        result.registrationId(),
        result.status().name(),
        result.walletId(),
        result.walletAddress(),
        result.registeredAt(),
        result.nextAction().name(),
        result.userMessage(),
        result.supportMessageKey(),
        Web3.from(result.web3()));
  }

  public record Web3(
      Resource resource,
      String actionType,
      ExecutionIntent executionIntent,
      Execution execution,
      SignRequest signRequest,
      String signRequestUnavailableReason,
      boolean existing) {

    static Web3 from(WalletApprovalExecutionWriteView view) {
      if (view == null) {
        return null;
      }
      return new Web3(
          new Resource(view.resource().type(), view.resource().id(), view.resource().status()),
          view.actionType(),
          new ExecutionIntent(
              view.executionIntent().id(),
              view.executionIntent().status(),
              view.executionIntent().expiresAt(),
              view.executionIntent().expiresAtEpochSeconds()),
          new Execution(view.execution().mode(), view.execution().signCount()),
          SignRequest.from(view.signRequest()),
          view.signRequestUnavailableReason(),
          view.existing());
    }
  }

  public record Resource(String type, String id, String status) {}

  public record ExecutionIntent(
      String id, String status, java.time.LocalDateTime expiresAt, long expiresAtEpochSeconds) {}

  public record Execution(String mode, int signCount) {}

  public record SignRequest(Authorization authorization, Submit submit, Transaction transaction) {

    static SignRequest from(WalletApprovalExecutionWriteView.SignRequest request) {
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

    static Authorization from(WalletApprovalExecutionWriteView.Authorization request) {
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

    static Submit from(WalletApprovalExecutionWriteView.Submit request) {
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

    static Transaction from(WalletApprovalExecutionWriteView.Transaction request) {
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
