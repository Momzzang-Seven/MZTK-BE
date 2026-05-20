package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Marketplace module local view of the shared reward-token properties.
 *
 * <p>This keeps marketplace wiring independent from QnA infrastructure configuration classes while
 * still reading the same deployed token settings.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.reward-token")
public class MarketplaceRewardTokenProperties {

  @NotNull private Boolean enabled;
  @NotBlank private String tokenContractAddress;

  @NotNull
  @Min(0)
  private Integer decimals;
}
