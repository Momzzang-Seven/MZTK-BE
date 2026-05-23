package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import java.math.BigInteger;

public record MarketplaceEscrowOrderResult(
    String orderKey,
    BigInteger price,
    String tokenAddress,
    long deadlineEpochSeconds,
    int state,
    String buyerAddress,
    String trainerAddress) {

  public boolean isAbsent() {
    return state == 0;
  }
}
