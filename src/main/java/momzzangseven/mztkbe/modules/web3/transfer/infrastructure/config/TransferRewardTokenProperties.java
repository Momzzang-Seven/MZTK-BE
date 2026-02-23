package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Transfer module local view of web3.reward-token properties.
 *
 * <p>Used to avoid depending on token module's infrastructure configuration type.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "web3.reward-token")
public class TransferRewardTokenProperties {

  private boolean enabled = true;
  private String tokenContractAddress;
  private int decimals = 18;
  private Treasury treasury = new Treasury();
  private Prevalidate prevalidate = new Prevalidate();
  private Gas gas = new Gas();
  private Worker worker = new Worker();

  @Getter
  @Setter
  public static class Treasury {
    private String treasuryAddress;
  }

  @Getter
  @Setter
  public static class Prevalidate {
    private BigDecimal ethWarningThreshold = BigDecimal.ZERO;
    private BigDecimal ethCriticalThreshold = BigDecimal.ZERO;
  }

  @Getter
  @Setter
  public static class Worker {
    private int retryBackoffSeconds = 60;
  }

  @Getter
  @Setter
  public static class Gas {
    private long defaultGasLimit = 120_000L;
    private long defaultMaxPriorityFeePerGasWei = 1_000_000_000L;
    private int maxFeeMultiplier = 2;
  }
}
