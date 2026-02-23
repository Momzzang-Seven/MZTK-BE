package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SponsorDailyUsage {

  private final Long id;
  private final Long userId;
  private final LocalDate usageDateKst;
  private final BigInteger estimatedCostWei;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  public SponsorDailyUsage addEstimatedCost(BigInteger deltaWei) {
    return toBuilder().estimatedCostWei(estimatedCostWei.add(deltaWei)).build();
  }
}
