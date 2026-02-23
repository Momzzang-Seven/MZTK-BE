package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Transaction module local view of web3.reward-token properties.
 *
 * <p>Used to avoid depending on token module's infrastructure configuration type.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "web3.reward-token")
public class TransactionRewardTokenProperties {

  private boolean enabled = true;
  private String tokenContractAddress;
  private Treasury treasury = new Treasury();
  private Worker worker = new Worker();

  @Getter
  @Setter
  public static class Treasury {
    private String walletAlias = "reward-treasury";
    private String keyEncryptionKeyB64;
  }

  @Getter
  @Setter
  public static class Worker {
    private int claimTtlSeconds = 120;
    private int receiptTimeoutSeconds = 900;
    private int receiptPollMinSeconds = 1;
    private int receiptPollMaxSeconds = 5;
    private int retryBackoffSeconds = 60;
  }
}
