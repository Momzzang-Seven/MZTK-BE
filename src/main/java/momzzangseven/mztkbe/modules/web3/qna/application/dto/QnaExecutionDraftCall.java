package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record QnaExecutionDraftCall(String target, BigInteger value, String data) {

  public QnaExecutionDraftCall {
    if (target == null || target.isBlank()) {
      throw new Web3InvalidInputException("target is required");
    }
    if (value == null || value.signum() < 0) {
      throw new Web3InvalidInputException("value must be >= 0");
    }
    if (data == null || data.isBlank()) {
      throw new Web3InvalidInputException("data is required");
    }
  }
}
