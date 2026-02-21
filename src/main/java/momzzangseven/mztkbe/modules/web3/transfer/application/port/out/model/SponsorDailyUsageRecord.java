package momzzangseven.mztkbe.modules.web3.transfer.application.port.out.model;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SponsorDailyUsageRecord {

  private Long id;
  private Long userId;
  private LocalDate usageDateKst;
  private BigInteger estimatedCostWei;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
