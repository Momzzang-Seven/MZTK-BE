package momzzangseven.mztkbe.modules.web3.challenge.application.port.in;

import momzzangseven.mztkbe.modules.web3.challenge.application.dto.MarkChallengeUsedCommand;

public interface MarkChallengeUsedUseCase {

  void execute(MarkChallengeUsedCommand command);
}
