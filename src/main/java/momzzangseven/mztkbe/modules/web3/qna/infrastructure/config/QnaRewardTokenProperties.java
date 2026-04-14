package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaRewardTokenConfigPort;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.reward-token")
public class QnaRewardTokenProperties implements LoadQnaRewardTokenConfigPort {

  @NotNull private Boolean enabled;
  @NotBlank private String tokenContractAddress;

  @NotNull
  @Min(0)
  private Integer decimals;

  @Override
  public RewardTokenConfig loadRewardTokenConfig() {
    return new RewardTokenConfig(tokenContractAddress, decimals);
  }
}
