package momzzangseven.mztkbe.modules.web3.challenge.application.port.in;

import momzzangseven.mztkbe.modules.web3.challenge.application.dto.MarkChallengeExpiredCommand;

public interface MarkChallengeExpiredUseCase {

  void execute(MarkChallengeExpiredCommand command);
}
