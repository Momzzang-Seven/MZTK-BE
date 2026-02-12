package momzzangseven.mztkbe.modules.web3.token.infrastructure.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Reward token configuration bound from web3.reward-token.* */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.reward-token")
public class RewardTokenProperties {

  private boolean enabled = true;

  @NotBlank private String tokenContractAddress;

  @Min(0)
  private int decimals = 18;

  private Treasury treasury = new Treasury();
  private Prevalidate prevalidate = new Prevalidate();
  private Worker worker = new Worker();

  @Getter
  @Setter
  public static class Treasury {
    private String keyEncryptionKeyB64;
    private String treasuryAddress;
    private Provisioning provisioning = new Provisioning();

    @Getter
    @Setter
    public static class Provisioning {
      private boolean enabled;
    }
  }

  @Getter
  @Setter
  public static class Prevalidate {

    @DecimalMin("0")
    private BigDecimal ethWarningThreshold = BigDecimal.ZERO;

    @DecimalMin("0")
    private BigDecimal ethCriticalThreshold = BigDecimal.ZERO;
  }

  @Getter
  @Setter
  public static class Worker {
    @Min(1)
    private int claimTtlSeconds = 120;

    @Min(1)
    private int receiptTimeoutSeconds = 900;

    @Min(1)
    private int receiptPollMinSeconds = 1;

    @Min(1)
    private int receiptPollMaxSeconds = 5;

    @Min(1)
    private int retryBackoffSeconds = 60;
  }
}
