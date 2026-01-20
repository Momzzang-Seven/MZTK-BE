package momzzangseven.mztkbe.modules.web3.challenge.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.Challenge;

public interface LoadChallengePort {

  /** Find challenge by nonce */
  Optional<Challenge> findByNonce(String nonce);

  /** Find challenge by nonce and purpose */
  Optional<Challenge> findByNonceAndPurpose(String nonce, String purpose);
}
