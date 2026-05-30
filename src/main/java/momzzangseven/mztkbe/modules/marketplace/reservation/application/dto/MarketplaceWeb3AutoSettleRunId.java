package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record MarketplaceWeb3AutoSettleRunId(String value) {

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  public MarketplaceWeb3AutoSettleRunId {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException("schedulerRunId is required");
    }
    if (value.length() > 120) {
      throw new Web3InvalidInputException("schedulerRunId must be 120 characters or less");
    }
    value = value.trim();
  }

  public static MarketplaceWeb3AutoSettleRunId generate(Clock clock) {
    Instant now = Instant.now(clock);
    String timestamp = LocalDateTime.ofInstant(now, clock.getZone()).format(FORMATTER);
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    return new MarketplaceWeb3AutoSettleRunId("mkt-auto-settle-" + timestamp + "-" + suffix);
  }
}
