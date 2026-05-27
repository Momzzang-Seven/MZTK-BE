package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Sponsor nonce coordination settings shared by reward and execution issuers. */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.sponsor-nonce")
public class SponsorNonceProperties {

  @Min(1)
  private int openWindowSize = 3;
}
