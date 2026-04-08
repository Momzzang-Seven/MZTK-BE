package momzzangseven.mztkbe.modules.web3.execution.domain.vo;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ExecutionRetryPolicy(int retryBackoffSeconds) {

  public ExecutionRetryPolicy {
    if (retryBackoffSeconds <= 0) {
      throw new Web3InvalidInputException("retryBackoffSeconds must be positive");
    }
  }
}
