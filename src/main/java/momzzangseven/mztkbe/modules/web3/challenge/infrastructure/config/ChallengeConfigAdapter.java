package momzzangseven.mztkbe.modules.web3.challenge.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.out.LoadChallengeConfigPort;
import momzzangseven.mztkbe.modules.web3.challenge.domain.vo.ChallengeConfig;
import org.springframework.stereotype.Component;

/**
 * Adapter for loading challenge configuration
 *
 * <p>Implements the port interface to provide configuration from Spring properties.
 */
@Component
@RequiredArgsConstructor
public class ChallengeConfigAdapter implements LoadChallengeConfigPort {

  private final ChallengeProperties challengeProperties;

  @Override
  public ChallengeConfig loadConfig() {
    var eip4361 = challengeProperties.getEip4361();

    return new ChallengeConfig(
        challengeProperties.getTtlSeconds(),
        eip4361.getDomain(),
        eip4361.getUri(),
        eip4361.getVersion(),
        eip4361.getChainId());
  }
}
