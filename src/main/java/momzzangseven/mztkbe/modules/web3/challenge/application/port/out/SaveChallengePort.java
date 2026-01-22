package momzzangseven.mztkbe.modules.web3.challenge.application.port.out;

import momzzangseven.mztkbe.modules.web3.challenge.domain.model.Challenge;

public interface SaveChallengePort {
  Challenge save(Challenge challenge);
}
