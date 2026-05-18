package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.wallet.registration.approval")
public class WalletApprovalProperties {

  @NotNull private Boolean enabled;

  @Min(1)
  private long chainId;

  @NotBlank private String delegationBatchImplAddress;

  @NotBlank private String tokenContractAddress;

  @NotBlank private String qnaEscrowSpenderAddress;

  @NotBlank private String marketplaceEscrowSpenderAddress;

  @Min(1)
  private int ttlSeconds;
}
