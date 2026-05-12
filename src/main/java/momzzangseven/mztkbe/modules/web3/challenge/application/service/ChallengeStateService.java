package momzzangseven.mztkbe.modules.web3.challenge.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.challenge.ChallengeNotFoundException;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.ChallengeSnapshot;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.LoadChallengeQuery;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.MarkChallengeExpiredCommand;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.MarkChallengeUsedCommand;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.in.LoadChallengeUseCase;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.in.MarkChallengeExpiredUseCase;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.in.MarkChallengeUsedUseCase;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.out.LoadChallengePort;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.out.SaveChallengePort;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.Challenge;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengePurpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Challenge state access use cases for other modules. */
@Service
@RequiredArgsConstructor
public class ChallengeStateService
    implements LoadChallengeUseCase, MarkChallengeUsedUseCase, MarkChallengeExpiredUseCase {

  private final LoadChallengePort loadChallengePort;
  private final SaveChallengePort saveChallengePort;

  @Override
  @Transactional(readOnly = true)
  public Optional<ChallengeSnapshot> execute(LoadChallengeQuery query) {
    return loadChallengePort
        .findByNonceAndPurpose(query.nonce(), parsePurpose(query.purpose()))
        .map(ChallengeSnapshot::from);
  }

  @Override
  @Transactional
  public void execute(MarkChallengeUsedCommand command) {
    Challenge challenge = loadChallenge(command.nonce(), parsePurpose(command.purpose()));
    saveChallengePort.save(challenge.markAsUsed());
  }

  @Override
  @Transactional
  public void execute(MarkChallengeExpiredCommand command) {
    Challenge challenge = loadChallenge(command.nonce(), parsePurpose(command.purpose()));
    saveChallengePort.save(challenge.markAsExpired());
  }

  private Challenge loadChallenge(String nonce, ChallengePurpose purpose) {
    return loadChallengePort
        .findByNonceAndPurpose(nonce, purpose)
        .orElseThrow(ChallengeNotFoundException::new);
  }

  private ChallengePurpose parsePurpose(String purpose) {
    return ChallengePurpose.valueOf(purpose);
  }
}
