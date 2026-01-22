package momzzangseven.mztkbe.modules.web3.challenge.infrastructure.persistence.repository;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengePurpose;
import momzzangseven.mztkbe.modules.web3.challenge.infrastructure.entity.ChallengeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeJpaRepository extends JpaRepository<ChallengeEntity, String> {

  Optional<ChallengeEntity> findByNonce(String nonce);

  Optional<ChallengeEntity> findByNonceAndPurpose(String nonce, ChallengePurpose purpose);
}
