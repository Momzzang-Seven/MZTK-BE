package momzzangseven.mztkbe.modules.web3.marketplace.domain.vo;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.web3j.utils.Numeric;

/** Encodes marketplace reservation identifiers into smart-contract compatible values. */
public final class MarketplaceEscrowIdCodec {

  private static final String ZERO_BYTES32 = "0x" + "0".repeat(64);

  private MarketplaceEscrowIdCodec() {}

  /** Converts a reservation primary key into a bytes32 order key. */
  public static String orderKey(Long reservationId) {
    if (reservationId == null || reservationId <= 0) {
      throw new Web3InvalidInputException("reservationId must be positive");
    }
    return Numeric.toHexStringWithPrefixZeroPadded(BigInteger.valueOf(reservationId), 64);
  }

  public static String zeroBytes32() {
    return ZERO_BYTES32;
  }
}
