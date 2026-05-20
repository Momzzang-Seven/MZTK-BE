package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

import java.math.BigInteger;
import java.util.Locale;

public record MarketplaceEscrowOrderView(
    String orderKey,
    BigInteger price,
    String tokenAddress,
    Long deadlineEpochSeconds,
    int state,
    String buyerAddress,
    String trainerAddress) {

  public static final int STATE_ABSENT = 0;
  public static final int STATE_CREATED = 1000;
  public static final int STATE_CONFIRMED = 2000;
  public static final int STATE_CANCELLED = 3000;
  public static final int STATE_ADMIN_SETTLED = 4000;
  public static final int STATE_ADMIN_REFUNDED = 5000;
  public static final int STATE_DEADLINE_REFUNDED = 6000;

  private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

  public boolean isAbsent() {
    return state == STATE_ABSENT || isZeroAddress(buyerAddress);
  }

  private boolean isZeroAddress(String address) {
    return address == null || ZERO_ADDRESS.equals(address.toLowerCase(Locale.ROOT));
  }
}
