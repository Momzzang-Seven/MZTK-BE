package momzzangseven.mztkbe.modules.web3.challenge.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.ChallengeSnapshot;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.LoadChallengeQuery;

public interface LoadChallengeUseCase {

  Optional<ChallengeSnapshot> execute(LoadChallengeQuery query);
}
