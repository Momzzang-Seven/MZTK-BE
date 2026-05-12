package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Batch command for scheduled wallet registration recovery reconciliation. */
public record RunWalletRegistrationRecoveryBatchCommand(int batchSize) {

  public RunWalletRegistrationRecoveryBatchCommand {
    if (batchSize <= 0) {
      throw new Web3InvalidInputException("batchSize must be positive");
    }
  }
}
