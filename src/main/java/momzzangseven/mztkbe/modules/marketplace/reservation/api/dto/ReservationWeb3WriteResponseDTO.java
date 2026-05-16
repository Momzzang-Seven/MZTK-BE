package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;

public record ReservationWeb3WriteResponseDTO(
    Resource resource,
    String actionType,
    String orderKey,
    ExecutionIntent executionIntent,
    Execution execution,
    SignRequest signRequest,
    String signRequestUnavailableReason,
    boolean existing,
    SignatureMeta signatureMeta,
    TokenMovement tokenMovement) {

  public static ReservationWeb3WriteResponseDTO from(ReservationExecutionWriteView view) {
    if (view == null) {
      return null;
    }
    return new ReservationWeb3WriteResponseDTO(
        new Resource(view.resource().type(), view.resource().id(), view.resource().status()),
        view.actionType(),
        view.orderKey(),
        new ExecutionIntent(
            view.executionIntent().id(),
            view.executionIntent().status(),
            view.executionIntent().expiresAt(),
            view.executionIntent().expiresAtEpochSeconds()),
        new Execution(view.execution().mode(), view.execution().signCount()),
        SignRequest.from(view.signRequest()),
        view.signRequestUnavailableReason(),
        view.existing(),
        SignatureMeta.from(view.signatureMeta()),
        TokenMovement.from(view.tokenMovement()));
  }

  public record Resource(String type, String id, String status) {}

  public record ExecutionIntent(
      String id, String status, LocalDateTime expiresAt, Long expiresAtEpochSeconds) {}

  public record Execution(String mode, int signCount) {}

  public record SignRequest(Authorization authorization, Submit submit, Transaction transaction) {

    static SignRequest from(ReservationExecutionWriteView.SignRequest request) {
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

    static Authorization from(ReservationExecutionWriteView.Authorization request) {
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

    static Submit from(ReservationExecutionWriteView.Submit request) {
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

    static Transaction from(ReservationExecutionWriteView.Transaction request) {
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

  public record SignatureMeta(Long signedAt, Long signatureExpiresAt) {

    static SignatureMeta from(ReservationExecutionWriteView.SignatureMeta meta) {
      if (meta == null) {
        return null;
      }
      return new SignatureMeta(meta.signedAt(), meta.signatureExpiresAt());
    }
  }

  public record TokenMovement(
      String tokenAddress,
      String amountBaseUnits,
      String fromRole,
      String fromAddress,
      String toRole,
      String toAddress) {

    static TokenMovement from(ReservationExecutionWriteView.TokenMovement movement) {
      if (movement == null) {
        return null;
      }
      return new TokenMovement(
          movement.tokenAddress(),
          movement.amountBaseUnits(),
          movement.fromRole(),
          movement.fromAddress(),
          movement.toRole(),
          movement.toAddress());
    }
  }
}
