package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record CreateExecutionIntentCommand(
    ExecutionDraft draft, ExecutionIntentIdempotencyMismatchPolicy mismatchPolicy) {

  public CreateExecutionIntentCommand {
    if (draft == null) {
      throw new Web3InvalidInputException("draft is required");
    }
    if (mismatchPolicy == null) {
      mismatchPolicy =
          ExecutionIntentIdempotencyMismatchPolicy.CANCEL_AWAITING_SIGNATURE_AND_CREATE_NEW;
    }
  }

  public CreateExecutionIntentCommand(ExecutionDraft draft) {
    this(draft, ExecutionIntentIdempotencyMismatchPolicy.CANCEL_AWAITING_SIGNATURE_AND_CREATE_NEW);
  }
}
