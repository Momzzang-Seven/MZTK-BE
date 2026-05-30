package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record MarketplaceWeb3AutoSettlePolicy(
    int batchSize, int scanSize, int maxScanPagesPerBatch, int maxBatchesPerRun) {

  public MarketplaceWeb3AutoSettlePolicy {
    if (batchSize < 1 || batchSize > 500) {
      throw new Web3InvalidInputException("batchSize must be between 1 and 500");
    }
    if (scanSize < batchSize || scanSize > 2000) {
      throw new Web3InvalidInputException("scanSize must be between batchSize and 2000");
    }
    if (maxScanPagesPerBatch < 1 || maxScanPagesPerBatch > 20) {
      throw new Web3InvalidInputException("maxScanPagesPerBatch must be between 1 and 20");
    }
    if (maxBatchesPerRun < 1 || maxBatchesPerRun > 100) {
      throw new Web3InvalidInputException("maxBatchesPerRun must be between 1 and 100");
    }
  }
}
