package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record MarketplaceAdminExecutionDraftSubmitResult(
    String executionIntentId,
    String executionIntentStatus,
    String executionMode,
    LocalDateTime expiresAt,
    boolean existing,
    String payloadSnapshotJson) {

  public MarketplaceAdminExecutionDraftSubmitResult {
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId is required");
    }
    if (executionIntentStatus == null || executionIntentStatus.isBlank()) {
      throw new Web3InvalidInputException("executionIntentStatus is required");
    }
    if (executionMode == null || executionMode.isBlank()) {
      throw new Web3InvalidInputException("executionMode is required");
    }
    if (expiresAt == null) {
      throw new Web3InvalidInputException("expiresAt is required");
    }
  }
}
