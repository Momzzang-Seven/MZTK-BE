package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.eip712")
public class ExecutionEip712Properties {
  @NotBlank private String domainName;
  @NotBlank private String domainVersion;
  @NotNull @Positive private Long chainId;
  @NotBlank private String verifyingContract;
}
