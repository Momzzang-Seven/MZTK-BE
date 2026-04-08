package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;

public record ExecutionActionPlan(
    BigInteger amountWei, ExecutionReferenceType referenceType, List<ExecutionDraftCall> calls) {

  public ExecutionActionPlan {
    if (amountWei == null || amountWei.signum() < 0) {
      throw new Web3InvalidInputException("amountWei must be >= 0");
    }
    if (referenceType == null) {
      throw new Web3InvalidInputException("referenceType is required");
    }
    if (calls == null) {
      throw new Web3InvalidInputException("calls is required");
    }
  }
}
