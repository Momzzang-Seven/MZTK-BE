package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;

/** Encodes user-scope marketplace escrow calldata. */
public interface BuildMarketplaceEscrowCallDataPort {

  String encode(
      MarketplaceExecutionActionType actionType,
      String orderKey,
      String tokenAddress,
      String trainerAddress,
      BigInteger priceBaseUnits,
      Long signedAt,
      byte[] signatureBytes);
}
