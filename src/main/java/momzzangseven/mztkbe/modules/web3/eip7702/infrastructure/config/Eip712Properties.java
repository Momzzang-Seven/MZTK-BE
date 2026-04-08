package momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Local view of web3.eip712 properties for EIP-7702 signature/digest handling. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "web3.eip712")
public class Eip712Properties {
  private String domainName;
  private String domainVersion;
  private Long chainId;
  private String verifyingContract;
}
