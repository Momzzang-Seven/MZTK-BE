package momzzangseven.mztkbe.modules.web3.challenge.application.port.in;

import momzzangseven.mztkbe.modules.web3.challenge.application.dto.CreateChallengeCommand;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.CreateChallengeResult;

public interface CreateChallengeUseCase {
  CreateChallengeResult execute(CreateChallengeCommand command);
}
