package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Transfer module local view of web3.eip712 properties.
 *
 * <p>Used to avoid depending on signature module's infrastructure configuration type.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "web3.eip712")
public class TransferEip712Properties {
  private String domainName;
  private String domainVersion;
  private Long chainId;
  private String verifyingContract;
}
