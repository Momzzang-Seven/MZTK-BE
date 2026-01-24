package momzzangseven.mztkbe.modules.web3.challenge.application.port.out;

import momzzangseven.mztkbe.modules.web3.challenge.domain.vo.ChallengeConfig;

/**
 * Port for loading challenge configuration
 *
 * <p>Abstracts configuration loading from infrastructure layer, following Hexagonal Architecture
 * principles.
 */
public interface LoadChallengeConfigPort {

  /**
   * Load challenge configuration
   *
   * @return challenge configuration containing EIP-4361 parameters
   */
  ChallengeConfig loadConfig();
}
