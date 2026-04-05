package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ExecutionDraftCall(String toAddress, BigInteger valueWei, String data) {

  public ExecutionDraftCall {
    if (toAddress == null || toAddress.isBlank()) {
      throw new Web3InvalidInputException("toAddress is required");
    }
    if (valueWei == null || valueWei.signum() < 0) {
      throw new Web3InvalidInputException("valueWei must be >= 0");
    }
    if (data == null || data.isBlank()) {
      throw new Web3InvalidInputException("data is required");
    }
  }
}
