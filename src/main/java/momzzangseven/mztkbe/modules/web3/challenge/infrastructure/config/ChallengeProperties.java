package momzzangseven.mztkbe.modules.web3.challenge.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration Properties for challenge - binding properties under web3.challenge in
 * application.yml
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.challenge")
public class ChallengeProperties {
  private int ttlSeconds = 300;

  private Eip4361 eip4361 = new Eip4361();

  @Getter
  @Setter
  public static class Eip4361 {

    private String domain = "MZTK";

    private String uri = "https://mztk.com";

    private String version = "1";

    /** Blockchain network Chain ID - 1: Ethereum Mainnet - 11155111: Sepolia Testnet */
    private String chainId = "11155111";
  }
}
