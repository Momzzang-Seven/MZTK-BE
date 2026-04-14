package momzzangseven.mztkbe.modules.answer.api.dto;

import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionWriteView;

/** Public API projection of answer lifecycle write payload. */
public record AnswerWeb3WriteResponse(
    Resource resource,
    String actionType,
    ExecutionIntent executionIntent,
    Execution execution,
    SignRequest signRequest,
    boolean existing) {

  /** Returns {@code null} when no new answer execution intent was prepared. */
  public static AnswerWeb3WriteResponse from(AnswerExecutionWriteView view) {
    if (view == null) {
      return null;
    }
    return new AnswerWeb3WriteResponse(
        new Resource(view.resource().type(), view.resource().id(), view.resource().status()),
        view.actionType(),
        new ExecutionIntent(
            view.executionIntent().id(),
            view.executionIntent().status(),
            view.executionIntent().expiresAt()),
        new Execution(view.execution().mode(), view.execution().signCount()),
        SignRequest.from(view.signRequest()),
        view.existing());
  }

  public record Resource(String type, String id, String status) {}

  public record ExecutionIntent(String id, String status, java.time.LocalDateTime expiresAt) {}

  public record Execution(String mode, int signCount) {}

  public record SignRequest(Authorization authorization, Submit submit, Transaction transaction) {

    static SignRequest from(AnswerExecutionWriteView.SignRequest request) {
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

    static Authorization from(AnswerExecutionWriteView.Authorization request) {
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

    static Submit from(AnswerExecutionWriteView.Submit request) {
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

    static Transaction from(AnswerExecutionWriteView.Transaction request) {
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
