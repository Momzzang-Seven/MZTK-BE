package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.signature.infrastructure.config.EIP712Properties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Fail-fast guard for chain-id consistency across Web3 SSOT and EIP-712 configuration. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class Eip7702ChainConsistencyValidator {

  private final Web3CoreProperties web3CoreProperties;
  private final EIP712Properties eip712Properties;

  @PostConstruct
  void validate() {
    if (web3CoreProperties.getChainId() != eip712Properties.getChainId()) {
      throw new IllegalStateException(
          "web3.chain-id and web3.eip712.chain-id must match for EIP-7702 flow");
    }
  }
}
