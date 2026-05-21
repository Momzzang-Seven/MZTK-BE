package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.math.BigInteger;

public record MarketplaceAdminTokenView(
    String tokenAddress, BigInteger amountBaseUnits, String symbol) {}
