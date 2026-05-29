package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.nonce;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.nonce.NonceSlotEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NonceSlotEvidenceJpaRepository
    extends JpaRepository<NonceSlotEvidenceEntity, Long> {

  Optional<NonceSlotEvidenceEntity> findByIdAndChainIdAndFromAddressAndNonce(
      Long id, Long chainId, String fromAddress, Long nonce);
}
