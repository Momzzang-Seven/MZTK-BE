package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Transfer module local view of web3.reward-token properties.
 *
 * <p>Used to avoid depending on token module's infrastructure configuration type.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.reward-token")
public class TransferRewardTokenProperties {

  @NotNull private Boolean enabled;
  @NotBlank private String tokenContractAddress;
  @NotNull @Min(0) private Integer decimals;
  @Valid private Treasury treasury = new Treasury();
  @Valid private Prevalidate prevalidate = new Prevalidate();
  @Valid private Gas gas = new Gas();
  @Valid private Worker worker = new Worker();

  @Getter
  @Setter
  public static class Treasury {
    @NotBlank private String treasuryAddress;
  }

  @Getter
  @Setter
  public static class Prevalidate {
    @NotNull @DecimalMin("0") private BigDecimal ethWarningThreshold;
    @NotNull @DecimalMin("0") private BigDecimal ethCriticalThreshold;
  }

  @Getter
  @Setter
  public static class Worker {
    @Min(1) private int retryBackoffSeconds;
  }

  @Getter
  @Setter
  public static class Gas {
    @Min(21_000) private long defaultGasLimit;
    @Min(1) private long defaultMaxPriorityFeePerGasWei;
    @Min(1) private int maxFeeMultiplier;
  }
}
