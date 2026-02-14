package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Core web3 infrastructure configuration bound from web3.*. */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3")
public class Web3CoreProperties {

  @Min(1)
  private long chainId;

  @Valid private Rpc rpc = new Rpc();

  @Getter
  @Setter
  public static class Rpc {
    @NotBlank private String main;
    @NotBlank private String sub;
  }
}
