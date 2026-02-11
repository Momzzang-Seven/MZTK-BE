package momzzangseven.mztkbe.modules.web3.signature.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * EIP-712 Domain Configuration Properties
 *
 * <p>Provides identical Domain configuration as frontend.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.eip712")
public class EIP712Properties {
  /** Domain name */
  @NotBlank
  private String domainName;

  /** Domain version */
  @NotBlank
  private String domainVersion;

  /** Chain ID */
  @NotNull
  @Positive
  private Long chainId;

  /** Verifying contract address */
  @NotBlank
  private String verifyingContract;
}
