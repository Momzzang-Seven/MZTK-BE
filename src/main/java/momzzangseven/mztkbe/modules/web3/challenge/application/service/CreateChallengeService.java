package momzzangseven.mztkbe.modules.web3.challenge.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.CreateChallengeCommand;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.CreateChallengeResult;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.in.CreateChallengeUseCase;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.out.LoadChallengeConfigPort;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.out.SaveChallengePort;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.Challenge;
import org.springframework.stereotype.Service;

/** Challenge creation service */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateChallengeService implements CreateChallengeUseCase {

  private final SaveChallengePort saveChallengePort;
  private final LoadChallengeConfigPort loadChallengeConfigPort;

  @Override
  public CreateChallengeResult execute(CreateChallengeCommand command) {
    log.info(
        "Creating challenge: userId={}, purpose={}, address={}",
        command.userId(),
        command.purpose(),
        command.walletAddress());

    // 0. Validate command
    command.validate();

    // 1. Load configuration
    var config = loadChallengeConfigPort.loadConfig();

    // 2. Create Challenge object
    Challenge challenge =
        Challenge.create(command.userId(), command.purpose(), command.walletAddress(), config);

    // 3. Save challenge
    Challenge savedChallenge = saveChallengePort.save(challenge);

    log.info(
        "Challenge created successfully: nonce={}, expiresAt={}",
        savedChallenge.getNonce(),
        savedChallenge.getExpiresAt());

    return CreateChallengeResult.from(savedChallenge, config.ttlSeconds());
  }
}
