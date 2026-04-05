package momzzangseven.mztkbe.modules.web3.execution.domain.vo;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ExecutionCleanupPolicy(String zone, int retentionDays, int batchSize) {

  public ExecutionCleanupPolicy {
    if (zone == null || zone.isBlank()) {
      throw new Web3InvalidInputException("zone is required");
    }
    if (retentionDays <= 0) {
      throw new Web3InvalidInputException("retentionDays must be positive");
    }
    if (batchSize <= 0) {
      throw new Web3InvalidInputException("batchSize must be positive");
    }
  }
}
