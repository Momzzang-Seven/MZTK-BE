package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Transfer module local view of web3 core properties.
 *
 * <p>Used to avoid depending on transaction module's infrastructure configuration type.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "web3")
public class TransferCoreProperties {

  private long chainId;
  private Rpc rpc = new Rpc();

  @Getter
  @Setter
  public static class Rpc {
    private String main;
    private String sub;
  }
}
