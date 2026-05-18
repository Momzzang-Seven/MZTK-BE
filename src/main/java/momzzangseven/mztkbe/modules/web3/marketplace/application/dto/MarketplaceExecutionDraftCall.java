package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Marketplace-owned contract call prepared for a user execution draft. */
public record MarketplaceExecutionDraftCall(String target, BigInteger value, String data) {

  public MarketplaceExecutionDraftCall {
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
