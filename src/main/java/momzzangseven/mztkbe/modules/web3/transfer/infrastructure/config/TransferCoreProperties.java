package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Transfer module local view of web3 core properties.
 *
 * <p>Used to avoid depending on transaction module's infrastructure configuration type.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3")
public class TransferCoreProperties {

  @NotNull @Positive private Long chainId;
  @Valid private Rpc rpc = new Rpc();

  @Getter
  @Setter
  public static class Rpc {
    @NotBlank private String main;
    @NotBlank private String sub;
  }
}
