package momzzangseven.mztkbe.modules.web3.signature.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.signature.domain.model.EIP712Domain;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EIP-712 Configuration
 *
 * <p>Registers Domain configuration as Spring Bean.
 */
@Configuration
@RequiredArgsConstructor
public class EIP712Config {

  private final EIP712Properties properties;

  /** Create EIP712Domain bean */
  @Bean
  public EIP712Domain eip712Domain() {
    return EIP712Domain.builder()
        .name(properties.getDomainName())
        .version(properties.getDomainVersion())
        .chainId(properties.getChainId())
        .verifyingContract(properties.getVerifyingContract())
        .build();
  }
}
