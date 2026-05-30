package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.MarketplaceWeb3AutoSettlePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.marketplace.admin.auto-settle")
public class MarketplaceWeb3AutoSettleSchedulerProperties {

  @NotNull private Boolean enabled = Boolean.FALSE;

  @Min(1)
  @Max(500)
  private int batchSize = 50;

  @Min(1)
  @Max(2000)
  private int scanSize = 150;

  @Min(1)
  @Max(20)
  private int maxScanPagesPerBatch = 5;

  @Min(1)
  @Max(100)
  private int maxBatchesPerRun = 20;

  @NotBlank private String cron = "0 23 * * * *";

  @NotBlank private String zone = "Asia/Seoul";

  public MarketplaceWeb3AutoSettlePolicy loadPolicy() {
    return new MarketplaceWeb3AutoSettlePolicy(
        batchSize, scanSize, maxScanPagesPerBatch, maxBatchesPerRun);
  }
}
